# Changelog

All notable changes to Tickwatch are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-06-07

### Added
- Initial release.
- Server-side tracking of **playtime**, **kills**, and **deaths** per player.
- Multi-loader support for **Fabric** and **NeoForge** from a single codebase, with a
  Minecraft-free `common` module and no Architectury / wrapper dependency.
- Per-world JSON storage at `<world>/fiw-clock/stats.json`, including a human-readable
  `playTimeFormatted` field alongside the raw `playTimeTicks`.
- Crash-safe write pipeline: temp file + `fsync` + rotating `.bak` + atomic rename, with
  automatic backup recovery and per-entry fault tolerance on load.
- Off-thread asynchronous saving (every 5 minutes and on player logout) plus a final
  blocking save on server shutdown, so the server tick loop never blocks on disk I/O.

[1.0.0]: https://github.com/Fi3w0/Fiw-Clock/releases/tag/v1.0.0