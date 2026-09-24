package com.fiw

import com.fiw.api.StatsListener
import com.fiw.api.StatsSnapshot
import com.fiw.luckperms.LuckPermsBridge
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the in-memory stats and the tracking logic, independent of any mod loader.
 *
 * Every mutating method here is expected to be called from the server thread (the
 * platform event handlers all run there). [stats] is a concurrent map only so the
 * public API can take snapshots from other threads; disk I/O is pushed off-thread –
 * see [StatsStorage].
 */
object StatsManager {

	/** Autosave cadence. 6000 ticks = 5 minutes at 20 TPS. */
	private const val AUTOSAVE_INTERVAL_TICKS = 6000

	/** Retry cadence while waiting for LuckPerms to enable, and how long to keep trying. */
	private const val LUCKPERMS_RETRY_TICKS = 20
	private const val LUCKPERMS_MAX_ATTEMPTS = 60

	private val stats = ConcurrentHashMap<UUID, PlayerStats>()
	private val online = HashSet<UUID>()
	private val listeners = CopyOnWriteArrayList<StatsListener>()
	private var storage: StatsStorage? = null
	private var config = TickwatchConfig()
	private var tickCounter = 0
	private var dirty = false

	// LuckPerms meta sync state.
	internal var luckPerms: LuckPermsBridge? = null
		private set
	private val luckPermsDirty = HashSet<UUID>()
	private var luckPermsAttempts = 0
	private var luckPermsTickCounter = 0

	/** Wall clock, swappable for tests. */
	internal var clock: () -> Long = System::currentTimeMillis

	val isRunning: Boolean
		get() = storage != null

	/** Called when the server starts. [directory] is the per-world Tickwatch folder. */
	fun start(directory: Path) {
		config = TickwatchConfig.load(directory.resolve("config.json"))
		val store = StatsStorage(directory.resolve("stats.json"))
		storage = store
		stats.clear()
		stats.putAll(store.load())
		online.clear()
		tickCounter = 0
		dirty = false
		luckPerms = null
		luckPermsDirty.clear()
		luckPermsAttempts = if (config.luckPermsEnabled && LuckPermsBridge.present) 0 else LUCKPERMS_MAX_ATTEMPTS
		luckPermsTickCounter = 0
		Tickwatch.LOGGER.info("Loaded stats for {} player(s)", stats.size)
	}

	/** Call once per online player, every server tick. */
	fun tickPlayer(uuid: UUID, name: String) {
		entryFor(uuid, name).playTimeTicks++
		dirty = true
	}

	/** Call once after all players have been ticked; handles autosave and LuckPerms sync. */
	fun afterServerTick() {
		if (++tickCounter >= AUTOSAVE_INTERVAL_TICKS) {
			tickCounter = 0
			if (dirty) {
				flushAsync()
				dirty = false
			}
		}
		tickLuckPerms()
	}

	fun onPlayerJoin(uuid: UUID, name: String) {
		// Refresh the cached name in case the player changed it, creating the entry if new.
		val entry = entryFor(uuid, name)
		val now = clock()
		entry.name = name
		entry.joins++
		if (entry.firstJoin == 0L) entry.firstJoin = now
		entry.lastSeen = now
		online.add(uuid)
		luckPermsDirty.add(uuid)
		dirty = true
		notify(uuid, entry, StatsListener.Reason.JOIN)
	}

	fun onPlayerQuit(uuid: UUID, name: String) {
		val entry = entryFor(uuid, name)
		entry.name = name
		entry.lastSeen = clock()
		online.remove(uuid)
		luckPermsDirty.remove(uuid)
		// Persist promptly so a crash right after someone logs off can't lose their session.
		flushAsync()
		dirty = false
		notify(uuid, entry, StatsListener.Reason.QUIT)
	}

	/**
	 * [uuid] killed [victim]. [victimIsPlayer] splits the kill into player vs mob kills.
	 * Killing yourself (e.g. with your own arrow) is not counted.
	 */
	fun recordKill(uuid: UUID, name: String, victim: UUID, victimIsPlayer: Boolean) {
		if (uuid == victim) return
		val entry = entryFor(uuid, name)
		entry.kills++
		if (victimIsPlayer) entry.playerKills++ else entry.mobKills++
		entry.killStreak++
		if (entry.killStreak > entry.bestKillStreak) entry.bestKillStreak = entry.killStreak
		changed(uuid)
		notify(uuid, entry, if (victimIsPlayer) StatsListener.Reason.PLAYER_KILL else StatsListener.Reason.MOB_KILL)
	}

	fun recordDeath(uuid: UUID, name: String) {
		val entry = entryFor(uuid, name)
		entry.deaths++
		entry.killStreak = 0
		changed(uuid)
		notify(uuid, entry, StatsListener.Reason.DEATH)
	}

	/** Called when the server is stopping: blocking final save, then tear down. */
	fun stop() {
		val store = storage ?: return
		store.saveBlocking(snapshot())
		store.shutdown()
		storage = null
		stats.clear()
		online.clear()
		luckPerms = null
		luckPermsDirty.clear()
		dirty = false
		Tickwatch.LOGGER.info("Stats saved on shutdown")
	}

	// ---- public API support --------------------------------------------------

	internal fun snapshotOf(uuid: UUID): StatsSnapshot? =
		stats[uuid]?.let { StatsSnapshot(uuid, it.copy()) }

	internal fun snapshotOf(name: String): StatsSnapshot? =
		stats.entries.firstOrNull { it.value.name.equals(name, ignoreCase = true) }
			?.let { StatsSnapshot(it.key, it.value.copy()) }

	internal fun allSnapshots(): List<StatsSnapshot> =
		stats.entries.map { StatsSnapshot(it.key, it.value.copy()) }

	internal fun top(stat: Stat, limit: Int): List<StatsSnapshot> =
		allSnapshots()
			.sortedWith(compareByDescending<StatsSnapshot> { it.value(stat) }.thenBy { it.name.lowercase() })
			.take(limit.coerceAtLeast(0))

	internal fun addListener(listener: StatsListener) {
		listeners.addIfAbsent(listener)
	}

	internal fun removeListener(listener: StatsListener) {
		listeners.remove(listener)
	}

	// ---- internals -----------------------------------------------------------

	private fun entryFor(uuid: UUID, name: String): PlayerStats =
		stats.getOrPut(uuid) { PlayerStats(name) }

	private fun changed(uuid: UUID) {
		dirty = true
		luckPermsDirty.add(uuid)
	}

	private fun notify(uuid: UUID, entry: PlayerStats, reason: StatsListener.Reason) {
		if (listeners.isEmpty()) return
		val snapshot = StatsSnapshot(uuid, entry.copy())
		for (listener in listeners) {
			try {
				listener.onStatsChanged(snapshot, reason)
			} catch (e: Exception) {
				Tickwatch.LOGGER.error("Stats listener {} failed: {}", listener, e.toString())
			}
		}
	}

	private fun tickLuckPerms() {
		val bridge = luckPerms ?: run {
			if (luckPermsAttempts >= LUCKPERMS_MAX_ATTEMPTS || ++luckPermsTickCounter < LUCKPERMS_RETRY_TICKS) return
			luckPermsTickCounter = 0
			val connected = LuckPermsBridge.connect(config)
			if (connected == null) {
				if (++luckPermsAttempts >= LUCKPERMS_MAX_ATTEMPTS) {
					Tickwatch.LOGGER.warn("LuckPerms is installed but never became available; meta sync disabled")
				}
				return
			}
			Tickwatch.LOGGER.info("LuckPerms found, syncing stats to meta (prefix '{}')", config.metaPrefix)
			luckPerms = connected
			luckPermsTickCounter = 0
			luckPermsDirty.addAll(online)
			connected
		}

		// Playtime changes every tick, so it is pushed on a slower timer than kills/deaths.
		if (++luckPermsTickCounter >= config.playtimeSyncSeconds * 20) {
			luckPermsTickCounter = 0
			luckPermsDirty.addAll(online)
		}
		if (luckPermsDirty.isEmpty()) return

		for (uuid in luckPermsDirty) {
			val entry = stats[uuid] ?: continue
			try {
				bridge.push(uuid, entry)
			} catch (e: Exception) {
				Tickwatch.LOGGER.error("LuckPerms meta sync failed for {}: {}", entry.name, e.toString())
			}
		}
		luckPermsDirty.clear()
	}

	private fun flushAsync() {
		storage?.saveAsync(snapshot())
	}

	/** Deep copy taken on the server thread; the I/O thread only ever sees this snapshot. */
	private fun snapshot(): Map<UUID, PlayerStats> {
		val copy = HashMap<UUID, PlayerStats>(stats.size)
		for ((id, value) in stats) {
			copy[id] = value.copy()
		}
		return copy
	}
}
