# Tickwatch

**Track playtime, AFK time, sessions, player & mob kills, deaths and kill streaks — server-side, lightweight, crash-safe, LuckPerms-ready.**

Tickwatch quietly records how long each player has played (AFK time excluded) and how many kills
and deaths they rack up, then stores it all in a clean JSON file. No client mod required, no impact
on your TPS — and a `/tickwatch` command when you want to look.

---

## ✨ Why Tickwatch?

- 🪶 **Featherweight** — tracking is one integer per online player per tick. That's it.
- 🖥️ **Server-side only** — your players join with a **vanilla client**. Install it on the server and forget it.
- 🔀 **Fabric, NeoForge *and* Forge** — Minecraft 1.21.11, 1.21.1 and 1.20.1.
- 💾 **Crash-safe by design** — atomic writes with a rotating backup. A sudden power loss or hard
  crash **cannot corrupt your stats**.
- ⚡ **Never blocks the server** — all disk writes run on a dedicated background thread.
- 💤 **AFK-aware** — idle players stop earning playtime; AFK pools can't farm it either.
- 🏆 **`/tickwatch` command** — stats of any player (even offline) and leaderboards for any stat.
- 📣 **Milestone broadcasts** — optional *"Notch has played for 100 hours!"* announcements.
- 🔑 **LuckPerms integration** — stats become LuckPerms meta (`tickwatch_kills`, `tickwatch_playtime`,
  `tickwatch_afk`, …) so any chat, tab or scoreboard mod that reads LuckPerms meta can display them,
  and every command has a permission node.
- 🧩 **Public API** — other mods can read stats, leaderboards and listen for kills, deaths and AFK changes.

## 📊 What it tracks

For every player, per world:

| Stat | Detail |
|------|--------|
| ⏱️ **Playtime** | Active time in ticks, with a human-readable `2h 14m 30s` field |
| 💤 **AFK time** | Time online but idle, plus a live AFK flag |
| 🕒 **Sessions** | Current session and longest session ever |
| ⚔️ **Kills** | Total, plus **player kills** and **mob kills** separately |
| 💀 **Deaths** | Times that player died, plus **K/D ratio** |
| 🔥 **Kill streaks** | Current streak (reset on death) and best-ever streak |
| 📅 **Joins** | Login count, first-join date, last-seen date |

Stored at `<world>/fiw-clock/stats.json`:

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

Perfect for leaderboards, web dashboards, Discord bots, or anything that can read JSON.

## 💬 Commands

| Command | Permission node | Default |
|---------|-----------------|---------|
| `/tickwatch stats [player]` | `tickwatch.command.stats` (`.stats.others`) | everyone |
| `/tickwatch top <stat> [count]` | `tickwatch.command.top` | everyone |
| `/tickwatch reload` | `tickwatch.command.reload` | ops |

## 📦 Requirements

| Minecraft | Loaders | Java |
|-----------|---------|------|
| 1.21.11 | Fabric, NeoForge | 21+ |
| 1.21.1 | Fabric, NeoForge | 21+ |
| 1.20.1 | Fabric, Forge | 17+ |

- **Fabric:** Fabric API + Fabric Language Kotlin
- **NeoForge / Forge:** Kotlin for Forge

Optional: **LuckPerms** — enables meta sync (`/lp user <name> meta info` shows the stats).

No Architectury or other wrapper mod required.

## 🚀 Install

1. Install Fabric, NeoForge or Forge on your server.
2. Add the matching dependencies to `mods/` — **Fabric:** Fabric API + Fabric Language Kotlin; **NeoForge / Forge:** Kotlin for Forge.
3. Drop in the Tickwatch jar for your loader and Minecraft version.
4. Start the server — stats start tracking immediately.

> 💡 Tickwatch is purely server-side. Players do **not** need it installed to join.

## 📖 Links

- 📜 Source & issues: <https://github.com/Fi3w0/Fiw-Clock>
- 🆓 License: MIT