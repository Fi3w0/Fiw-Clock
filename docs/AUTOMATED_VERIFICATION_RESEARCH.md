# Automated verification research

Verified 2026-11-15 against released packages, primary documentation, installed source and the active mod adapters. This is a test design and API reference, not a claim that gameplay tests have passed.

## Runnable toolchain and exact versions

Use Node 22+ (local Node is 26.13.0), Mineflayer **4.39.0**, minecraft-protocol **1.68.0**, minecraft-data **3.119.0**. The coordinator has installed these exact versions under `.github/player-tests`; retain its lockfile and use `npm ci` for repeatable installs. Before installation, no installed Minecraft bot library was found in the inspected global, npx, Bun or Codex runtime directories; npm had protocol/data tarballs cached. [Released Mineflayer metadata](https://registry.npmjs.org/mineflayer/4.39.0), [protocol metadata](https://registry.npmjs.org/minecraft-protocol/1.68.0), [data metadata](https://registry.npmjs.org/minecraft-data/3.119.0).

An isolated equivalent setup is:

```sh
npm install --prefix /private/tmp/fiw-player-qa --no-audit --no-fund --save-exact mineflayer@4.39.0 minecraft-protocol@1.68.0 minecraft-data@3.119.0
```

| Exact Minecraft | Protocol | Production server targets |
|---|---:|---|
| 26.1.2 | 775 | Fabric |
| 1.21.11 | 774 | Fabric, NeoForge |
| 1.21.8 | 772 | Fabric, NeoForge |
| 1.21.1 | 767 | Fabric, NeoForge |
| 1.20.1 | 763 | Fabric, Forge |

Mineflayer 4.39.0's released `lib/version.js` explicitly lists the four 1.x versions and 26.1. Its loader accepts a later data version when the protocol equals its latest supported protocol. minecraft-data maps **26.1.2 to 26.1, protocol 775**, so a version-number alias patch is unnecessary. A local, connection-free probe loaded all five exact versions and successfully serialized their `use_entity` packets with the installed packages. Actual joins remain a separate test. [Released Mineflayer tarball](https://registry.npmjs.org/mineflayer/-/mineflayer-4.39.0.tgz), [loader source](https://github.com/PrismarineJS/mineflayer/blob/master/lib/loader.js), [protocol-version data](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/common/protocolVersions.json).

## Bot APIs and traps

Create a bot with `{host:'127.0.0.1', port, username:'FiwQA', auth:'offline', version:exactVersion, respawn:false}` against an isolated offline test server. Bind that server to loopback; keep production authentication/configuration unchanged. Register listeners before sending the triggering command/packet. Wait for `spawn`, then `await bot.waitForChunksToLoad()`; `login` is earlier than a usable player. `bot.respawn()` returns void: register the next `spawn` promise first, call it after `death`, then await the promise. Mineflayer handles teleport confirmations and the newer player-loaded packet. [Client options](https://github.com/PrismarineJS/node-minecraft-protocol/blob/master/docs/API.md#mc-createclientoptions), [Mineflayer API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md), [health implementation](https://github.com/PrismarineJS/mineflayer/blob/master/lib/plugins/health.js).

Graves markers are packet-only **armor stands** plus **text displays**. Observe `entitySpawn`/packet events, and choose the marker near the expected grave location; server selectors cannot find it. Normal interaction can use `bot.activateEntity(marker)` followed by `windowOpen`. For sneak collection, Mineflayer's normal activation hardcodes `sneaking:false`, so send the exact raw interaction instead:

```js
// 1.20.1, 1.21.1, 1.21.8, 1.21.11
bot._client.write('use_entity', { target: marker.id, mouse: 0, hand: 0, sneaking: true })
// 26.1.2: action/hand schema changed; location is a relative vector
bot._client.write('use_entity', {
  target: marker.id, hand: 'main_hand', location: { x: 0, y: 0, z: 0 }, sneaking: true
})
```

Both forms were successfully serialized locally. Stay within Graves' seven-block interaction radius. Raw writes are the documented protocol client's `write(name, params)` API; `_client` is Mineflayer's lower-level client seam. [Inventory implementation](https://github.com/PrismarineJS/mineflayer/blob/master/lib/plugins/inventory.js), [26.1 schema](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/26.1/protocol.json), [1.21.1 schema](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.21.1/protocol.json).

- **Grave contents:** `window.containerItems()` or `window.slots.slice(0, window.inventoryStart)`. `window.items()` returns the player's inventory section. The plugin adds `window.withdraw(type, metadata, count, nbt)`; there is no `bot.withdraw`. Normal `clickWindow(slot,0,0)` is documented as stable; shift-click is experimental. Await each operation and the server's inventory update before assertions. [Window API](https://github.com/PrismarineJS/prismarine-windows/blob/master/API.md), [container API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md#windowwithdrawitemtype-metadata-count-nbt).
- **Item equality:** fingerprint item name, count, metadata, NBT, components and removed components, using stable key ordering and BigInt-safe serialization. Exclude transient slot indexes; sort component entries by type without reordering meaningful nested lists. Installed `Item.equal` ignores data components, so it cannot prove enchantment/component preservation. 1.20.1 uses legacy NBT; newer versions carry enchantments in components. Compare each target's pre-death inventory to that target's recovered inventory, rather than comparing wire IDs across versions. [Item implementation](https://github.com/PrismarineJS/prismarine-item/blob/master/index.js).
- **XP:** `bot.experience.points` is the raw `totalExperience` packet field. It can disagree with level/progress after `/experience set`. Capture level/progress and compute total using the game's level curve; level 15 at progress zero is **315 XP**. Seed/reset deliberately and assert XP is granted once, not merely that it increases. [Experience plugin](https://github.com/PrismarineJS/mineflayer/blob/master/lib/plugins/experience.js), local `DeathCapture.getTotalExperience`.

## Required Graves scenarios on every one of the nine targets

Use the actual four production jars together, real bot players and disposable worlds. Give each case a distinct item fingerprint and record every phase in machine-readable output. The ordinary player must be non-op when testing access checks; operators bypass ownership. The following assertions come from the active Graves source in `common-*/grave`, `entity/GraveMenu`, and the raw-interaction mixin.

1. **Death and durable capture:** seed a sword with Sharpness V, damaged equipment, offhand item, stackable diamonds and nonzero XP. Record the full inventory/armor/offhand fingerprint. Kill the bot in survival with keep-inventory disabled. Require exactly one grave, no duplicated normal death drops, the expected owner/dimension, and empty respawn inventory. Stop **before collection**, restart the same world, reconnect, and require the marker, exact contents and XP still exist.
2. **GUI and partial collection:** open the grave using the received fake entity ID. XP is granted **on opening**, before item pickup; close/reopen and ensure no second XP grant. Move only part of a stack using ordinary clicks, close, save/stop/restart, and verify collected plus remaining counts match the original. Collect the rest and ensure the grave disappears without items returning on another restart.
3. **Quick collection:** sneak-interact with the correct raw packet. Check every original stack, component, armor slot and XP, not just the diamond count. Interact again and reconnect: no duplication. Test with automatic armor equipment both enabled and disabled.
4. **Full inventory and partial merge:** fill all 36 inventory slots; leave one same-item stack with room for only part of a grave stack. Quick collect. Current code intentionally **drops leftovers on the ground** and empties those grave slots. Assert conservation across player inventory, equipped armor, nearby item entities and remaining grave. Clear space and collect ground drops; recheck exact total. A durable admin backup is not a spendable duplicate and must be excluded from that conservation total.
5. **Placement:** normal ground, water, lava, void, obstructed location and Nether death. Require a reachable supported marker and preserved contents after restart; do not accept `/graves list` alone as evidence of usable interaction. Check same-dimension access and missing markers after leaving/re-entering visibility range or reconnecting.
6. **Rules and ownership:** non-owner/non-op refusal; normal owner access; keep-inventory default and explicit override; blacklisted items dropped versus stored; XP storage on/off/fraction; disabled dimension and excluded damage type. Each refusal must leave loot and XP conserved.
7. **Lifecycle and recovery:** two deaths, grave limit eviction, expiry with drops on/off, retained loot backups, admin restore, partial collection followed by restart, death while a menu is open, and repeated interaction packets. Restore is intentionally capable of recreating an already-collected backup; test this privileged recovery independently of ordinary no-duplication assertions.
8. **Serialization regression fixtures:** zero items, one item plus padded empty slots, 41 inventory/equipment slots, more than 54 captured stacks, full legacy NBT, and registry-backed enchantments/potions on component versions. Require exact codec round trips and reload counts. At least one real restart is mandatory; an in-memory encode/decode alone misses SavedData wiring and dirty-state errors.

Fail the test if grave encode/decode logs an error even when the server exits zero. Preserve server logs, bot logs, before/after fingerprints and the world data from the failure.

## Persistence findings and test seams

At inspection time, **1.21.1 `GraveManager.load/save` accepts `HolderLookup.Provider` but passes bare `NbtOps.INSTANCE` to the manager codec**. That manager contains `ItemStack.OPTIONAL_CODEC`. Official 1.21.1 Minecraft `ItemStack.parse/save` bytecode instead calls `registries.createSerializationContext(NbtOps.INSTANCE)`. The exact public method exists, and registry-backed codecs need the lookup context. Test an enchanted item and a registry-backed potion before accepting this port; a plain diamond can miss the problem. This is a source-confirmed risk, not a recorded failing gameplay run. [Official NeoForge 1.21.1 codec guidance](https://docs.neoforged.net/docs/1.21.1/datastorage/codecs/), [exact Mojang artifact metadata](https://piston-meta.mojang.com/v1/packages/9aa86ea608c3ec777dcdc09e6c5a06c85b53599e/1.21.1.json).

For 1.20.1, the current public `Grave.CODEC` wraps `CompoundTag.CODEC` with `ItemStack.of`/`stack.save(new CompoundTag())` and serializes a padded slot list. Test `ItemStack.EMPTY`, count, damage, name, lore, enchantments and nested item data using **NbtOps**, not JSON conversion: NBT list element types are stricter than JSON numbers. For 1.21.1, use the real running server's registry access, not a mock registry that lacks datapack enchantments. `Grave.CODEC` is a narrow public fixture seam; actual `GraveManager` save/stop/restart exercises the enclosing active/rollback/backup collections and dirty listeners. Its `resultOrPartial(...).orElseGet(new manager)` path makes log assertions essential: a partial decode/default manager can otherwise resemble an empty successful world. [Codec error/partial-result semantics](https://docs.neoforged.net/docs/1.21.1/datastorage/codecs/).

Another negative fixture follows from source inspection: 1.21.1 `GraveService.onInteract` checks XYZ distance and ownership but does not compare the player's dimension to the grave's dimension. Receive a marker ID in the Overworld, move the bot to matching Nether coordinates, then send the stale ID: require no menu, XP grant or inventory mutation. The ID lookup is server-wide, so ordinary visibility alone is insufficient authorization. This is an unexecuted test recommendation, not a reproduced exploit result.

## Shared AFK and Warp matrix

Run these on all nine servers after the Graves conservation gate:

| Surface | Assertions |
|---|---|
| Shared AFK | `/fiw afk` marks AFK; Tickwatch stops active-time increments and increments AFK time; second toggle clears both; camera rotation via `bot.look` clears manual/automatic AFK; translation/teleport alone does not count as camera activity; configurable short idle timeout changes both; reload with Admin AFK disabled clears authoritative state; reconnect/restart resets runtime marks while preserving accumulated Tickwatch totals; a separate run without Admin verifies Tickwatch fallback. |
| Warp | Disable admin bypass or use a non-op. `/warp name` prompts without moving; accept starts warmup, deny/expiry does not teleport; successful teleport updates position/dimension and `/back`; physical movement over 0.05 blocks, damage, attacking and block breaking independently cancel when enabled; disabled cancellation switches allow completion; cooldown kinds remain independent; missing destination dimension and cross-dimension-disabled cases refuse; homes/warps survive restart; reconnect cannot revive a pending warmup. |

The active sources use wall time for Admin idle/confirmation expiry and ticks for Tickwatch accounting/Warp warmup. Assert observable state with bounded polling rather than assuming identical time bases. Subscribe before commands, use a stable flat platform, stop movement after each test, and disable irrelevant natural hazards. Mineflayer offers `chat`, `look`, control states, attack/dig APIs, position/dimension state, and forced-movement events. [Bot API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md).

## GameTest alternatives and unattended release gate

Bots are the fastest cross-loader seam for these server-only features because they execute real login, death, menu, packet interaction and teleport paths. For deeper server assertions, use a separate test mod/GameTest source set compiled for each Minecraft API version; never assume a modern annotation/registration example works on 1.20.1.

- **Fabric 26.1.2:** official docs show `fabricApi.configureTests`, a separate `gametest` source set/metadata, server tests integrated with `build`, client tests via `runClientGameTest`, and production client test tasks with Xvfb for Linux CI. Fabric Loader JUnit initializes Mixin-aware tests; registry-dependent classes also require Minecraft bootstrap. The older 1.21.1 page only documents unit testing, so inspect exact API artifacts before configuring old GameTests. [26.1.2 guide](https://docs.fabricmc.net/26.1.2/develop/automatic-testing), [1.21.1 guide](https://docs.fabricmc.net/1.21.1/develop/automatic-testing).
- **Forge 1.20.1 / NeoForge 1.21.1:** documented `runGameTestServer` exits according to required failures; use required tests and explicit completion assertions. Registration differs by loader. [Forge exact-version guide](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/), [NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/misc/gametest/).
- **NeoForge 1.21.8 / 1.21.11:** modern tests combine an environment, structure, test function and test instance; do not copy the old annotation-only setup. Both versioned guides document `runGameTestServer`. [1.21.8](https://docs.neoforged.net/docs/1.21.8/misc/gametest/), [1.21.11](https://docs.neoforged.net/docs/1.21.11/misc/gametest/).

The unattended gate is: core tests → all jar builds → nine production boots → nine complete bot scenario runs including two restarts → codec/component fixtures → retained reports. Mark each assertion passed/failed, fail on timeouts and unexpected error logs, and publish only after every required target passes. Vanilla bots do not validate rendered skins, hologram appearance or a custom modded-client protocol; packet metadata tests and automated real-client GameTests/screenshots cover those surfaces. No manual player approval is needed to run this matrix, and no finite matrix establishes correctness for arbitrary third-party modpacks.


## Findings from the production client runs

- MC 1.21.11 already uses namespaced gamerules such as `minecraft:keep_inventory`;
  1.21.8 and older use `keepInventory`. Checking the command result matters.
- Grave head entity Y includes configurable `markerHeight` (default -0.6). Placement
  assertions subtract that offset before comparing the actual grave with a fluid surface.
- A visible marker may already exist before a `/graves tp` client command has executed.
  Use the ordered console input stream with `execute as <player> at @s run graves tp`
  for deterministic setup; entity click, GUI withdrawal and inventory assertions still
  travel through the actual vanilla protocol.
- Confirm dimensions using an in-dimension server predicate and a unique tellraw token;
  protocol-library dimension metadata and asynchronous transitions vary between versions.
  Settle cross-dimension respawns before forcing another death.
- Disable test-client physics and allow flight only in disposable test server properties.
  This avoids false vanilla flying kicks. Test teleport dimension policy with an immune
  fixture so random Nether terrain damage cannot cancel the request being tested.
- Rebuild shared Kotlin outputs serially. Runtime-only parallel checks use completed jars
  and already-installed test tools (`integrationTest -PuseBuiltJars=true`). Reinstalling
  node_modules while other clients are live can invalidate their dependencies.
