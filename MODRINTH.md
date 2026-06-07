# Tickwatch

**Track playtime, kills, and deaths — server-side, lightweight, crash-safe.**

Tickwatch quietly records how long each player has played and how many kills and deaths they
rack up, then stores it all in a clean JSON file. No client mod required, no commands to learn,
no impact on your TPS.

---

## ✨ Why Tickwatch?

- 🪶 **Featherweight** — tracking is one integer per online player per tick. That's it.
- 🖥️ **Server-side only** — your players join with a **vanilla client**. Install it on the server and forget it.
- 🔀 **Fabric *and* NeoForge** — same mod, both loaders.
- 💾 **Crash-safe by design** — atomic writes with a rotating backup. A sudden power loss or hard
  crash **cannot corrupt your stats**.
- ⚡ **Never blocks the server** — all disk writes run on a dedicated background thread.

## 📊 What it tracks

For every player, per world:

| Stat | Detail |
|------|--------|
| ⏱️ **Playtime** | Counted in ticks, with a human-readable `2h 14m 30s` field |
| ⚔️ **Kills** | Mobs and players killed by that player |
| 💀 **Deaths** | Times that player died |

Stored at `<world>/fiw-clock/stats.json`:

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

Perfect for leaderboards, web dashboards, Discord bots, or anything that can read JSON.

## 📦 Requirements

**Minecraft 1.21.11 · Java 21+**

- **Fabric:** Fabric API + Fabric Language Kotlin
- **NeoForge:** Kotlin for Forge

No Architectury or other wrapper mod required.

## 🚀 Install

1. Install Fabric or NeoForge on your server.
2. Add the matching dependencies to `mods/` — **Fabric:** Fabric API + Fabric Language Kotlin; **NeoForge:** Kotlin for Forge.
3. Drop in the Tickwatch jar for your loader.
4. Start the server — stats start tracking immediately.

> 💡 Tickwatch is purely server-side. Players do **not** need it installed to join.

## 📖 Links

- 📜 Source & issues: <https://github.com/Fi3w0/Fiw-Clock>
- 🆓 License: MIT