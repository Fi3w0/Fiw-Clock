# Tickwatch

**Tickwatch** is a tiny, server-side Minecraft mod that records each player's **playtime**,
**AFK time**, **sessions**, **player & mob kills**, **deaths**, **kill streaks** and **join history**,
stores them in a plain JSON file per world, and (optionally) mirrors them into **LuckPerms meta** so
other mods can display them. It runs on **Fabric**, **NeoForge** and **Forge** across Minecraft
**1.21.11, 1.21.1 and 1.20.1** from a single codebase — **no Architectury or other wrapper
dependency required**.

> Internally the project/mod id is `fiw-clock`; the public name is **Tickwatch**.

## Features

- **Rich stats per player** — see [Stats](#stats).
- **AFK-aware playtime** — idle players stop earning playtime; their time is counted as AFK time instead.
- **`/tickwatch` command** — stats of any player and leaderboards in chat (see [Commands](#commands)).
- **Milestone broadcasts** — optional chat announcements like *"Notch has played for 100 hours!"*.
- **LuckPerms integration** — stats become LuckPerms meta that any chat/tab/scoreboard mod can show
  (see [LuckPerms](#luckperms-integration)).
- **Public API** — other mods can read stats, leaderboards and change events (see [API](#api-for-other-mods)).
- **Fully server-side** — install it on the server only. Vanilla clients can connect normally;
  no client install is required (it registers no client code, networking, or screens).
- **Multi-loader, multi-version** — one build produces every jar: Fabric + NeoForge for 1.21.11 and
  1.21.1, Fabric + Forge for 1.20.1.
- **TPS-friendly** — tracking is a single integer increment per online player per tick, and all
  disk writes happen on a dedicated background thread. Nothing blocks the server thread.
- **Crash-safe storage** — writes are atomic with a rotating backup, so a sudden power loss or
  `kill -9` can never corrupt your data (see [How saving works](#how-saving-works)).

## How it works

| Event | What Tickwatch does |
|-------|---------------------|
| Server starting | Loads `stats.json` (or its backup) into memory |
| Every server tick | `+1` playtime tick (or AFK tick) and `+1` session tick for each online player |
| Player stops turning their camera for 5 min | Marked AFK until they look around again |
| Player joins | `+1` join, first-join date set once, last-seen updated, new session |
| Player kills a mob/player | `+1` kill (player or mob kill), `+1` kill streak |
| Player dies | `+1` death, kill streak reset |
| A stat reaches a milestone | Chat announcement (if milestones are enabled) |
| Every 5 minutes & on logout | Asynchronous snapshot saved to disk |
| On any stat change / every 60 s | Changed stats pushed to LuckPerms meta (if installed) |
| Server stopping | Final blocking save, then the I/O thread shuts down |

## Stats

| Stat id | Meaning |
|---------|---------|
| `name` | Last known username |
| `playtime` | Active time played, e.g. `2h 14m 30s` (stored as ticks; AFK time not included) |
| `playtime_hours` | Whole hours played |
| `afk_time` | Time spent online but AFK, e.g. `0h 12m 00s` |
| `kills` | All kills (players + mobs) |
| `player_kills` | Players killed |
| `mob_kills` | Mobs killed |
| `deaths` | Times died |
| `kdr` | Kills per death, e.g. `1.50` |
| `kill_streak` | Kills since the last death |
| `best_kill_streak` | Highest streak ever reached |
| `joins` | Number of logins |
| `first_join` | Date of first login (`yyyy-MM-dd`, UTC) |
| `last_seen` | Date of last login/logout (`yyyy-MM-dd`, UTC) |
| `session` | Length of the current session (`0h 00m 00s` while offline) |
| `longest_session` | Longest session ever |
| `afk` | `true` while the player is AFK, otherwise `false` |

Killing yourself (e.g. with your own arrow) doesn't count as a kill. A player becomes AFK after
`afk.minutes` (default 5) without turning their camera; being pushed around by water streams or
pistons doesn't count as activity, so AFK pools can't farm playtime.

## Commands

| Command | Permission node | Default |
|---------|-----------------|---------|
| `/tickwatch` or `/tickwatch stats` | `tickwatch.command.stats` | everyone |
| `/tickwatch stats <player>` | `tickwatch.command.stats.others` | everyone |
| `/tickwatch top <stat> [count]` | `tickwatch.command.top` | everyone |
| `/tickwatch reload` | `tickwatch.command.reload` | ops (level 2) |

- `stats` works for offline players too, and tab-completes every player Tickwatch has seen.
- `top` accepts any stat id except `name` and `afk` and shows up to 50 players (default 10).
- `reload` re-reads `config.json` without a restart; if the file is invalid the current settings are kept.
- With LuckPerms installed, the nodes above decide (e.g. `/lp group default permission set
  tickwatch.command.top false`). Without it, or when a node isn't set, the vanilla permission level
  applies: `commands.permissionLevel` in the config (default `0` = everyone) for `stats`/`top`, ops
  for `reload`.

## LuckPerms integration

LuckPerms is **optional**. When it is installed, Tickwatch writes each stat to the player's
LuckPerms meta as `tickwatch_<stat id>`, for example:

```
tickwatch_kills = 12
tickwatch_player_kills = 3
tickwatch_playtime = 2h 00m 00s
```

- See it in-game with `/lp user <name> meta info`.
- Any mod or plugin that can show LuckPerms meta (chat formatters, tab lists, scoreboards,
  placeholder mods) can now display Tickwatch stats.
- Kills, deaths, joins and AFK status sync right away; playtime and sessions sync every 60 seconds
  (configurable).
- Show an `[AFK]` tag with `tickwatch_afk`, or put `tickwatch_playtime_hours` in a tab list.
- The meta is **transient**: it's never saved to the LuckPerms database and is rebuilt on each
  login, so uninstalling Tickwatch leaves nothing behind.

## Configuration

`<world>/fiw-clock/config.json` is created on first start:

```json
{
  "luckperms": {
    "enabled": true,
    "metaPrefix": "tickwatch_",
    "playtimeSyncSeconds": 60,
    "stats": ["name", "playtime", "playtime_hours", "afk_time", "kills", "player_kills", "mob_kills",
              "deaths", "kdr", "kill_streak", "best_kill_streak", "joins", "first_join", "last_seen",
              "session", "longest_session", "afk"]
  },
  "afk": {
    "enabled": true,
    "minutes": 5
  },
  "commands": {
    "permissionLevel": 0
  },
  "milestones": {
    "enabled": false,
    "rules": [
      { "stat": "playtime_hours", "at": [1, 10, 50, 100, 250, 500, 1000], "message": "&6{player} &ehas played for &6{value} &ehours!" },
      { "stat": "kills", "at": [10, 50, 100, 250, 500, 1000], "message": "&6{player} &ereached &6{value} &ekills!" },
      { "stat": "kill_streak", "at": [5, 10, 25, 50], "message": "&6{player} &eis on a &6{value} &ekill streak!" },
      { "stat": "joins", "at": [10, 100, 500, 1000], "message": "&6{player} &ehas joined &6{value} &etimes!" }
    ]
  }
}
```

| Key | Default | Meaning |
|-----|---------|---------|
| `luckperms.enabled` | `true` | Mirror stats into LuckPerms meta when LuckPerms is installed |
| `luckperms.metaPrefix` | `tickwatch_` | Prefix of every meta key |
| `luckperms.playtimeSyncSeconds` | `60` | How often playtime and sessions are pushed (min 1) |
| `luckperms.stats` | all | Which stat ids are mirrored |
| `afk.enabled` | `true` | Detect AFK players; their time counts as `afk_time` instead of `playtime` |
| `afk.minutes` | `5` | Minutes without turning the camera before a player is AFK (min 1) |
| `commands.permissionLevel` | `0` | Vanilla permission level for `/tickwatch stats` and `top` when LuckPerms doesn't decide (`0` = everyone, `2` = ops) |
| `milestones.enabled` | `false` | Broadcast a chat message when a player reaches a milestone |
| `milestones.rules` | see above | `stat` (one of `playtime_hours`, `kills`, `player_kills`, `mob_kills`, `deaths`, `kill_streak`, `joins`), the values it announces `at`, and the `message` (`{player}`, `{value}` and `&` color codes) |

Changes apply on the next server start or after `/tickwatch reload`. If the file is invalid,
Tickwatch logs an error and keeps using the defaults (or, on reload, the current settings) without
overwriting it.

## API for other mods

Add Tickwatch as a compile-only dependency and use `com.fiw.api.TickwatchApi` (works from Java or
Kotlin, on every loader):

```java
StatsSnapshot s = TickwatchApi.get(player.getUUID());          // or getByName("Notch")
long mobKills = s.getMobKills();
boolean afk = s.isAfk();                                        // also isOnline(), getSessionTicks()
String playtime = s.format(Stat.PLAYTIME);                      // "2h 14m 30s"
List<StatsSnapshot> top = TickwatchApi.top(Stat.KILLS, 10);     // leaderboard
TickwatchApi.addListener((snapshot, reason) -> {
    /* JOIN, QUIT, PLAYER_KILL, MOB_KILL, DEATH, AFK_START, AFK_END */
});
```

Snapshots are immutable and safe to read from any thread. Listeners run on the server thread.

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
      "afkTicks": 14400,
      "kills": 12,
      "playerKills": 2,
      "mobKills": 10,
      "deaths": 3,
      "killStreak": 4,
      "bestKillStreak": 9,
      "joins": 27,
      "firstJoin": 1767225600000,
      "lastSeen": 1769904000000,
      "longestSessionTicks": 72000
    }
  }
}
```

- `playTimeTicks` is the source of truth (20 ticks = 1 second); AFK time is in `afkTicks`.
- `playTimeFormatted` is written for convenience and ignored when loading.
- `firstJoin` / `lastSeen` are epoch milliseconds (`0` = unknown, e.g. data from 1.0.0).
- Files written by older versions load fine; missing fields start at `0`.
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

| Minecraft | Loader | Java | Also needs |
|-----------|--------|------|------------|
| 1.21.11 | Fabric Loader 0.19.3+ | 21+ | [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.13.4+ |
| 1.21.11 | NeoForge 21.11+ | 21+ | [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) 6.0+ |
| 1.21.1 | Fabric Loader 0.16.9+ | 21+ | Fabric API, Fabric Language Kotlin 1.13.4+ |
| 1.21.1 | NeoForge 21.1+ | 21+ | Kotlin for Forge 5.10+ |
| 1.20.1 | Fabric Loader 0.16.10+ | 17+ | Fabric API, Fabric Language Kotlin 1.13.4+ |
| 1.20.1 | Forge 47+ | 17+ | Kotlin for Forge 4.12+ |

Optional on every target: [LuckPerms](https://luckperms.net) 5.4+ for meta sync and permission nodes.

## Installation

1. Install the matching loader (Fabric, NeoForge or Forge) on your server.
2. Drop the required dependencies into `mods/` (see the table above — Fabric needs Fabric API
   + Fabric Language Kotlin; NeoForge and Forge need Kotlin for Forge).
3. Drop the Tickwatch jar for your loader and Minecraft version into `mods/`, e.g.
   `fiw-clock-fabric-1.21.11-<version>.jar` or `fiw-clock-forge-1.20.1-<version>.jar`.
4. Start the server. Stats appear at `<world>/fiw-clock/stats.json`.

## Building from source

```bash
./gradlew build
```

Gradle runs on Java 21 (the 1.20.1 targets are compiled to Java 17 bytecode). Output jars land in
`<module>/build/libs/fiw-clock-<module>-<version>.jar`, e.g.
`fabric-1.21.11/build/libs/fiw-clock-fabric-1.21.11-<version>.jar`.

(Ignore the `*-sources.jar` files and anything in `build/devlibs` — those are development artifacts.)

### Project layout

```
core/                Loader-agnostic logic (tracking, AFK, storage, LuckPerms sync, commands' text, public API)
                     — Minecraft-free, unit-tested, bundled into every jar
common-<mc>/         Minecraft code shared by both loaders of one version (event glue, /tickwatch tree)
fabric-<mc>/         Fabric entry point + fabric.mod.json          (1.21.11, 1.21.1, 1.20.1)
neoforge-<mc>/       NeoForge entry point + neoforge.mods.toml     (1.21.11, 1.21.1)
forge-1.20.1/        Forge entry point + mods.toml
```

The `core` module touches no Minecraft or loader API at all — it works purely with player
UUIDs, names, and a `java.nio.Path`. Each loader module is a thin adapter that forwards its own
server events to `common-<mc>`, which calls into `core`. Per-target Minecraft/loader/Java versions
live in each module's `gradle.properties`; the mod version lives only in the root
`gradle.properties`.

### Releases

Pushing a new `mod_version` to `main` makes CI build every target, tag `v<version>`, create a
GitHub release with the `CHANGELOG.md` section as notes, and publish all six jars to Modrinth.

## License

Released under the [MIT License](LICENSE).