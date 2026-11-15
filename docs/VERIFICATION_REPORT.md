# Verification report — 2026-11-15

Prepared locally; no commits have been pushed and no new release has been published. See [the publication audit](COMPATIBILITY_AUDIT.md) for the current remote versions.

| Mod | Prepared version | Production jars |
|---|---|---|
| fiw clock | 1.2.0 | 9 |
| fiw admin utility's | 1.4.0 | 9 |
| fiw graves | 1.3.0 | 9 |
| fiw warp | 1.2.0 | 9 |

## Runtime results

All four mods were installed together with their actual runtime dependencies. Each target passed six production-server sessions, including repeated graceful saves and restarts, plus a separate combined AFK/teleport client connection.

| Target | Graves and restarts | Shared AFK and teleport behavior |
|---|---|---|
| fabric-26.1.2 | Passed | Passed |
| fabric-1.21.11 | Passed | Passed |
| neoforge-1.21.11 | Passed | Passed |
| fabric-1.21.8 | Passed | Passed |
| neoforge-1.21.8 | Passed | Passed |
| fabric-1.21.1 | Passed | Passed |
| neoforge-1.21.1 | Passed | Passed |
| fabric-1.20.1 | Passed | Passed |
| forge-1.20.1 | Passed | Passed |

Graves assertions cover visible death markers; all 17 fixture diamonds; Sharpness V sword NBT/components; equipped helmet, boots and offhand shield; 315 XP; stale cross-dimension interaction denial; exact partial GUI withdrawal and persisted remainder; one-time XP recovery; full-inventory partial stack merge; armor equip and overflow-drop conservation; collection and final persistence without resurrected graves; water/lava/void placement; and keepInventory without duplicate loot.

Combined gameplay assertions cover the actual optional AFK reflection bridge, manual AFK, camera activity, idle timeout, chat return and disabling the authority while AFK. Clock uses the shared state even with its own detector disabled. Warp checks homes, warmup cancellation, back, accept/deny confirmation and dimension policy. Separate standalone runs also passed Admin AFK on Fabric 1.21.1 and Warp persistence/gameplay on Fabric 26.1.2 across a restart.

## Build and release checks

- Full build matrix passed in all four repositories: 36 production jars.
- Clock core: 30 tests; Admin core: 16 tests; zero failures. The Clock reload regression was observed failing before its fix, then passed in the complete suite.
- All 36 packaged jars passed ZIP CRC, embedded-version, loader metadata and Java bytecode-level checks. Java 17 applies to 1.20.1, Java 21 to 1.21.x, Java 25 to 26.1.2.
- All nine Clock production jars contain the shared-AFK reload guard.
- All workflow files passed actionlint; Python, JavaScript and shell syntax and staged diffs were checked.
- Offline publishing dry run passed 36 payloads, dependency/channel/version metadata and description sync; retry skipped all existing versions. Required GitHub publishing secrets are present.
- Each repository has nine jars in its ignored release directory. Detailed runtime logs and saved-data snapshots remain under fiw clock/build/smoke/<target>; SHA-256 hashes are recorded in build/verification-results.json.

## Scope and reproduction

Fixtures use vanilla items and a vanilla-protocol client on a dedicated server. The runs do not establish graphical rendering, Bedrock compatibility, or compatibility with arbitrary third-party mods. AntiCheat, Custom Tools, Bosses and the Bedrock addon were intentionally deferred.

Build the four sibling repositories sequentially with `./gradlew build`, then run `./gradlew integrationTest -PsmokeTarget=<target>` from Fiw Clock. For parallel runtime checks after the build and test-client installation, use `-PuseBuiltJars=true`. Standalone CI runs its own mod on every target; Graves runs the full loot/hazard/restart suite before publishing. Logs upload even when assertions fail.
