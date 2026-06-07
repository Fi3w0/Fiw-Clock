# Tickwatch

**Tickwatch** is a tiny, server-side Minecraft mod that records each player's **playtime**,
**kills**, and **deaths** and stores them in a plain JSON file per world. It runs on both
**Fabric** and **NeoForge** from a single codebase using a plain multi-loader layout — **no
Architectury or other wrapper dependency required**.

> Internally the project/mod id is `fiw-clock`; the public name is **Tickwatch**.

## Features

- **Three stats per player** — playtime (in ticks, plus a human-readable string), kills, and deaths.
- **Fully server-side** — install it on the server only. Vanilla clients can connect normally;
  no client install is required (it registers no client code, networking, or screens).
- **Multi-loader** — one build produces a Fabric jar *and* a NeoForge jar.
- **TPS-friendly** — tracking is a single integer increment per online player per tick, and all
  disk writes happen on a dedicated background thread. Nothing blocks the server thread.
- **Crash-safe storage** — writes are atomic with a rotating backup, so a sudden power loss or
  `kill -9` can never corrupt your data (see [How saving works](#how-saving-works)).

## How it works

| Event | What Tickwatch does |
|-------|---------------------|
| Server starting | Loads `stats.json` (or its backup) into memory |
| Every server tick | `+1` playtime tick for each online player |
| Player kills a mob/player | `+1` kill for the attacking player |
| Player dies | `+1` death for that player |
| Every 5 minutes & on logout | Asynchronous snapshot saved to disk |
| Server stopping | Final blocking save, then the I/O thread shuts down |

## Data location & format

Stats are stored **per world** at:

```
<world>/fiw-clock/stats.json
```

```json
{
  "players": {
    "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
      "name": "Notch",
      "playTimeTicks": 144000,
      "playTimeFormatted": "2h 00m 00s",
      "kills": 12,
      "deaths": 3
    }
  }
}
```

- `playTimeTicks` is the source of truth (20 ticks = 1 second).
- `playTimeFormatted` is written for convenience and ignored when loading.
- A `stats.json.bak` (previous good copy) sits next to it for recovery.

## How saving works

Each save:

1. Serializes to `stats.json.tmp` and `fsync`s it to disk.
2. Rotates the current `stats.json` to `stats.json.bak`.
3. Atomically renames the temp file over `stats.json`.

A torn write can only ever damage `.tmp`; the live file and its backup are always complete.
On load, if `stats.json` is unreadable, Tickwatch automatically falls back to `stats.json.bak`,
and a single malformed entry is skipped rather than discarding the whole file.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21+ |
| Fabric | Fabric Loader 0.19.3+, [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) |
| NeoForge | 21.11+, [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) |

## Installation

1. Install the matching loader (Fabric or NeoForge) on your server.
2. Drop the required dependencies into `mods/` (see the table above — Fabric needs Fabric API
   + Fabric Language Kotlin; NeoForge needs Kotlin for Forge).
3. Drop the correct Tickwatch jar (`fiw-clock-fabric-*.jar` or `fiw-clock-neoforge-*.jar`) into `mods/`.
4. Start the server. Stats appear at `<world>/fiw-clock/stats.json`.

## Building from source

```bash
./gradlew build
```

Output jars land in:

- `fabric/build/libs/fiw-clock-fabric-<version>.jar`
- `neoforge/build/libs/fiw-clock-neoforge-<version>.jar`

(Ignore the `*-dev.jar` / `*-sources.jar` files — those are development artifacts.)

### Project layout

```
common/     Loader-agnostic logic (tracking + crash-safe storage) — completely Minecraft-free
fabric/     Fabric entry point + fabric.mod.json (wires Fabric API events to common)
neoforge/   NeoForge entry point + neoforge.mods.toml (wires NeoForge events to common)
```

The `common` module touches no Minecraft or loader API at all — it works purely with player
UUIDs, names, and a `java.nio.Path`. Each platform module is a thin adapter that forwards its
own server events into `common`.

## License

Released under the [MIT License](LICENSE).