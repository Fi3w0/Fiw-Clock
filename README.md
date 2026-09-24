# Tickwatch

**Tickwatch** is a tiny, server-side Minecraft mod that records each player's **playtime**,
**player & mob kills**, **deaths**, **kill streaks** and **join history**, stores them in a plain
JSON file per world, and (optionally) mirrors them into **LuckPerms meta** so other mods can display them. It runs on both
**Fabric** and **NeoForge** from a single codebase using a plain multi-loader layout — **no
Architectury or other wrapper dependency required**.

> Internally the project/mod id is `fiw-clock`; the public name is **Tickwatch**.

## Features

- **Rich stats per player** — see [Stats](#stats).
- **LuckPerms integration** — stats become LuckPerms meta that any chat/tab/scoreboard mod can show
  (see [LuckPerms](#luckperms-integration)).
- **Public API** — other mods can read stats, leaderboards and change events (see [API](#api-for-other-mods)).
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
| Player joins | `+1` join, first-join date set once, last-seen updated |
| Player kills a mob/player | `+1` kill (player or mob kill), `+1` kill streak |
| Player dies | `+1` death, kill streak reset |
| Every 5 minutes & on logout | Asynchronous snapshot saved to disk |
| On any stat change / every 60 s | Changed stats pushed to LuckPerms meta (if installed) |
| Server stopping | Final blocking save, then the I/O thread shuts down |

## Stats

| Stat id | Meaning |
|---------|---------|
| `playtime` | Time played, e.g. `2h 14m 30s` (stored as ticks) |
| `playtime_hours` | Whole hours played |
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

Killing yourself (e.g. with your own arrow) doesn't count as a kill.

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
- Kills, deaths and joins sync right away; playtime syncs every 60 seconds (configurable).
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
    "stats": ["playtime", "playtime_hours", "kills", "player_kills", "mob_kills", "deaths",
              "kdr", "kill_streak", "best_kill_streak", "joins", "first_join", "last_seen"]
  }
}
```

| Key | Default | Meaning |
|-----|---------|---------|
| `luckperms.enabled` | `true` | Mirror stats into LuckPerms meta when LuckPerms is installed |
| `luckperms.metaPrefix` | `tickwatch_` | Prefix of every meta key |
| `luckperms.playtimeSyncSeconds` | `60` | How often playtime is pushed (min 1) |
| `luckperms.stats` | all | Which stat ids are mirrored |

Changes apply on the next server start. If the file is invalid, Tickwatch logs an error and uses
the defaults without overwriting it.

## API for other mods

Add Tickwatch as a compile-only dependency and use `com.fiw.api.TickwatchApi` (works from Java or
Kotlin, on every loader):

```java
StatsSnapshot s = TickwatchApi.get(player.getUUID());          // or getByName("Notch")
long mobKills = s.getMobKills();
String playtime = s.format(Stat.PLAYTIME);                      // "2h 14m 30s"
List<StatsSnapshot> top = TickwatchApi.top(Stat.KILLS, 10);     // leaderboard
TickwatchApi.addListener((snapshot, reason) -> { /* JOIN, QUIT, PLAYER_KILL, MOB_KILL, DEATH */ });
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
      "kills": 12,
      "playerKills": 2,
      "mobKills": 10,
      "deaths": 3,
      "killStreak": 4,
      "bestKillStreak": 9,
      "joins": 27,
      "firstJoin": 1767225600000,
      "lastSeen": 1769904000000
    }
  }
}
```

- `playTimeTicks` is the source of truth (20 ticks = 1 second).
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

| | Version |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21+ |
| Fabric | Fabric Loader 0.19.3+, [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) |
| NeoForge | 21.11+, [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) |
| Optional | [LuckPerms](https://luckperms.net) 5.4+ for meta sync |

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
common/     Loader-agnostic logic (tracking, storage, LuckPerms sync, public API) — Minecraft-free, unit-tested
fabric/     Fabric entry point + fabric.mod.json (wires Fabric API events to common)
neoforge/   NeoForge entry point + neoforge.mods.toml (wires NeoForge events to common)
```

The `common` module touches no Minecraft or loader API at all — it works purely with player
UUIDs, names, and a `java.nio.Path`. Each platform module is a thin adapter that forwards its
own server events into `common`.

## License

Released under the [MIT License](LICENSE).