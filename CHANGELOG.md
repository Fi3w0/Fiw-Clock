# Changelog

All notable changes to Tickwatch are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-09-24

### Added
- **Minecraft 1.20.1 and 1.21.1 support**: Fabric + Forge for 1.20.1, Fabric + NeoForge for 1.21.1,
  next to Fabric + NeoForge for 1.21.11. Every jar is built from the same code.
- **Player kills** and **mob kills** tracked separately (the total `kills` is kept).
- **Kill streaks**: current streak (reset on death) and best-ever streak.
- **Join tracking**: number of joins, first-join date and last-seen date.
- **K/D ratio** available everywhere stats are exposed.
- **AFK-aware playtime**: a player who doesn't turn their camera for 5 minutes is AFK; that time
  is counted as `afk_time` instead of playtime (`afk.enabled` / `afk.minutes` in the config).
  Water streams and pistons don't count as activity, so AFK pools can't farm playtime.
- **Sessions**: current session length and longest session ever.
- **`/tickwatch` command**: `/tickwatch stats [player]` (works for offline players),
  `/tickwatch top <stat> [count]` and `/tickwatch reload`. Permission nodes
  `tickwatch.command.stats`, `.stats.others`, `.top` and `.reload` work with LuckPerms; without
  it everyone can use stats/top (`commands.permissionLevel`) and ops can reload.
- **Milestone broadcasts** (off by default): chat announcements such as
  *"Notch has played for 100 hours!"* for playtime hours, kills, kill streaks, deaths or joins,
  with configurable values and `&`-colored messages.
- **LuckPerms integration**: when LuckPerms is installed, every stat is mirrored into the
  player's LuckPerms meta (`tickwatch_name`, `tickwatch_kills`, `tickwatch_playtime`,
  `tickwatch_afk`, …), so any chat, tab or scoreboard mod that reads LuckPerms meta can display
  it. Check it with `/lp user <name> meta info`. Stored as transient data — never written to the
  LuckPerms database.
- **Public API** for other mods: `com.fiw.api.TickwatchApi` (lookups, leaderboards, change
  listeners including AFK start/end).
- Config file at `<world>/fiw-clock/config.json` (LuckPerms, AFK, commands, milestones).

### Changed
- Jars are now named per target: `fiw-clock-<loader>-<minecraft>-<version>.jar`.
- With AFK detection on (the default), `playtime` only counts active time. Set
  `afk.enabled` to `false` to keep counting all online time as playtime.
- Updated Fabric API for 1.21.11 to 0.141.6 and NeoForge to 21.11.45.

### Fixed
- The NeoForge jar failed to load: NeoForge doesn't allow `-` in mod ids. The NeoForge/Forge
  mod id is now `fiw_clock` (the Fabric id and the `<world>/fiw-clock/` data folder are unchanged).

### Notes
- Published as a **beta**: please report anything odd on the issue tracker.
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

[1.1.0]: https://github.com/Fi3w0/Fiw-Clock/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Fi3w0/Fiw-Clock/releases/tag/v1.0.0