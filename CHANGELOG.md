# Changelog

All notable changes to Tickwatch are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Player kills** and **mob kills** tracked separately (the total `kills` is kept).
- **Kill streaks**: current streak (reset on death) and best-ever streak.
- **Join tracking**: number of joins, first-join date and last-seen date.
- **K/D ratio** available everywhere stats are exposed.
- **LuckPerms integration**: when LuckPerms is installed, every stat is mirrored into the
  player's LuckPerms meta (`tickwatch_kills`, `tickwatch_playtime`, …), so any chat, tab or
  scoreboard mod that reads LuckPerms meta can display it. Check it with
  `/lp user <name> meta info`. Stored as transient data — never written to the LuckPerms database.
- **Public API** for other mods: `com.fiw.api.TickwatchApi` (lookups, leaderboards, change listeners).
- Config file at `<world>/fiw-clock/config.json` (LuckPerms on/off, meta prefix, sync interval,
  which stats to mirror).

### Changed
- The shared engine is now compiled for Java 17, in preparation for Minecraft 1.20.1 support.

### Notes
- Existing `stats.json` files load unchanged. Kills recorded before this version count toward
  the total only, since they were never split into player/mob kills.

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

[Unreleased]: https://github.com/Fi3w0/Fiw-Clock/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Fi3w0/Fiw-Clock/releases/tag/v1.0.0