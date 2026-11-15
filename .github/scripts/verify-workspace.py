#!/usr/bin/env python3
"""Verify all four sibling projects together using already-built production jars."""
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
WORKSPACE = ROOT.parent


def version(directory, key="mod_version"):
    props = dict(line.split("=", 1) for line in (directory / "gradle.properties").read_text().splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    return props[key].strip()


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "fabric-26.1.2"
    if target not in {"fabric-26.1.2", *(f"{loader}-{game}" for game in ["1.21.11", "1.21.8", "1.21.1"]
                                     for loader in ["fabric", "neoforge"]), "fabric-1.20.1", "forge-1.20.1"}:
        raise SystemExit("Unsupported loader/game target")
    extras = []
    for name, prefix, key in [("fiw admin utility's", "fiw-admin-tools", "mod_version"),
                              ("fiw graves", "fiwgraves", "version"), ("fiw warp", "fiw-warp", "mod_version")]:
        directory = WORKSPACE / name
        module = target.removesuffix("-1.21.11") if name == "fiw warp" and target.endswith("-1.21.11") else target
        jar = directory / module / "build/libs" / f"{prefix}-{target}-{version(directory, key)}.jar"
        extras.append(str(jar.resolve(strict=True)))
    suite = ROOT / "build" / f"workspace-suite-{target}.json"
    suite.parent.mkdir(exist_ok=True)
    suite.write_text(json.dumps({"jars": extras, "commands": ["fiw reload", "fiw status", "graves reload", "fiwwarp reload"],
                                "expected": ["AFK:", "reloaded"],
                                "player_script": str(WORKSPACE / "fiw graves/.github/player-tests/graves.cjs"),
                                "after_player_script": str(ROOT / ".github/player-tests/gameplay.cjs")}, indent=2))
    os.environ["NODE_PATH"] = str(ROOT / ".github/player-tests/node_modules")
    own = ROOT / target / "build/libs" / f"fiw-clock-{target}-{version(ROOT)}.jar"
    spec = importlib.util.spec_from_file_location("production_smoke", ROOT / ".github/scripts/smoke-test.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    sys.argv = ["smoke-test.py", target, str(own), "--suite-file", str(suite)]
    module.main()


if __name__ == "__main__":
    main()
