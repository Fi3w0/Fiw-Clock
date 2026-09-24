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
	private val rotations = HashMap<UUID, Rotation>()
	private val listeners = CopyOnWriteArrayList<StatsListener>()
	private var directory: Path? = null
	private var storage: StatsStorage? = null
	private var broadcast: (String) -> Unit = {}
	private var tickCounter = 0
	private var dirty = false

	internal var config = TickwatchConfig()
		private set

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

	/**
	 * Called when the server starts. [directory] is the per-world Tickwatch folder;
	 * [broadcast] sends an `&`-formatted chat message (see [Text]) to every online player.
	 */
	fun start(directory: Path, broadcast: (String) -> Unit = {}) {
		this.directory = directory
		this.broadcast = broadcast
		config = TickwatchConfig.load(directory.resolve("config.json"))
		val store = StatsStorage(directory.resolve("stats.json"))
		storage = store
		stats.clear()
		stats.putAll(store.load())
		online.clear()
		rotations.clear()
		tickCounter = 0
		dirty = false
		luckPerms = null
		luckPermsDirty.clear()
		resetLuckPermsConnection()
		Tickwatch.LOGGER.info("Loaded stats for {} player(s)", stats.size)
	}

	/**
	 * Call once per online player, every server tick, with the player's current camera
	 * rotation. A player who doesn't turn for `afk.minutes` becomes AFK: from then on the
	 * time counts as AFK time instead of playtime, until they look around again.
	 */
	fun tickPlayer(uuid: UUID, name: String, yaw: Float, pitch: Float) {
		val entry = entryFor(uuid, name)
		val rotation = rotations.getOrPut(uuid) { Rotation() }
		if (yaw != rotation.yaw || pitch != rotation.pitch) {
			rotation.yaw = yaw
			rotation.pitch = pitch
			rotation.idleTicks = 0
			if (entry.afk) setAfk(uuid, entry, false)
		} else if (config.afkEnabled && !entry.afk && ++rotation.idleTicks >= config.afkMinutes * Stat.TICKS_PER_MINUTE) {
			setAfk(uuid, entry, true)
		}

		if (entry.afk) {
			entry.afkTicks++
		} else if (++entry.playTimeTicks % Stat.TICKS_PER_HOUR == 0L) {
			milestone(entry, Stat.PLAYTIME_HOURS)
		}
		if (++entry.sessionTicks > entry.longestSessionTicks) entry.longestSessionTicks = entry.sessionTicks
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
		entry.online = true
		entry.afk = false
		entry.sessionTicks = 0
		online.add(uuid)
		rotations.remove(uuid)
		luckPermsDirty.add(uuid)
		dirty = true
		notify(uuid, entry, StatsListener.Reason.JOIN)
		milestone(entry, Stat.JOINS)
	}

	fun onPlayerQuit(uuid: UUID, name: String) {
		val entry = entryFor(uuid, name)
		entry.name = name
		entry.lastSeen = clock()
		entry.online = false
		entry.afk = false
		online.remove(uuid)
		rotations.remove(uuid)
		luckPermsDirty.remove(uuid)
		// Listeners still see the length of the session that just ended.
		notify(uuid, entry, StatsListener.Reason.QUIT)
		entry.sessionTicks = 0
		// Persist promptly so a crash right after someone logs off can't lose their session.
		flushAsync()
		dirty = false
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
		milestone(entry, Stat.KILLS)
		milestone(entry, if (victimIsPlayer) Stat.PLAYER_KILLS else Stat.MOB_KILLS)
		milestone(entry, Stat.KILL_STREAK)
	}

	fun recordDeath(uuid: UUID, name: String) {
		val entry = entryFor(uuid, name)
		entry.deaths++
		entry.killStreak = 0
		changed(uuid)
		notify(uuid, entry, StatsListener.Reason.DEATH)
		milestone(entry, Stat.DEATHS)
	}

	/**
	 * Re-reads `config.json`. Returns false (keeping the current settings) if the file is
	 * broken or the server isn't running. LuckPerms meta is rebuilt with the new settings.
	 */
	fun reload(): Boolean {
		val dir = directory ?: return false
		if (!isRunning) return false
		val fresh = TickwatchConfig.tryLoad(dir.resolve("config.json")) ?: return false

		val oldBridge = luckPerms
		if (oldBridge != null) {
			for (uuid in online) {
				try {
					oldBridge.clear(uuid)
				} catch (e: Exception) {
					Tickwatch.LOGGER.error("Could not clear LuckPerms meta for {}: {}", uuid, e.toString())
				}
			}
		}
		config = fresh
		luckPerms = null
		luckPermsDirty.clear()
		resetLuckPermsConnection()
		// Connect (and resend everything) on the next tick instead of waiting a full retry period.
		luckPermsTickCounter = LUCKPERMS_RETRY_TICKS - 1

		if (!config.afkEnabled) {
			for (uuid in online) {
				val entry = stats[uuid] ?: continue
				if (entry.afk) setAfk(uuid, entry, false)
			}
		}
		Tickwatch.LOGGER.info("Config reloaded")
		return true
	}

	/** Called when the server is stopping: blocking final save, then tear down. */
	fun stop() {
		val store = storage ?: return
		// Sessions don't survive a restart; don't let a stale "online" flag leak into snapshots.
		for (entry in stats.values) {
			entry.online = false
			entry.afk = false
			entry.sessionTicks = 0
		}
		store.saveBlocking(snapshot())
		store.shutdown()
		storage = null
		directory = null
		broadcast = {}
		stats.clear()
		online.clear()
		rotations.clear()
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

	/** Last camera rotation of an online player and how long it has been unchanged. */
	private class Rotation {
		var yaw = Float.NaN
		var pitch = Float.NaN
		var idleTicks = 0L
	}

	private fun entryFor(uuid: UUID, name: String): PlayerStats =
		stats.getOrPut(uuid) { PlayerStats(name) }

	private fun changed(uuid: UUID) {
		dirty = true
		luckPermsDirty.add(uuid)
	}

	private fun setAfk(uuid: UUID, entry: PlayerStats, afk: Boolean) {
		entry.afk = afk
		changed(uuid)
		notify(uuid, entry, if (afk) StatsListener.Reason.AFK_START else StatsListener.Reason.AFK_END)
	}

	private fun milestone(entry: PlayerStats, stat: Stat) {
		if (!config.milestonesEnabled) return
		val value = stat.value(entry).toLong()
		for (milestone in config.milestones) {
			if (milestone.stat != stat || value !in milestone.at) continue
			val message = milestone.message
				.replace("{player}", entry.name)
				.replace("{value}", value.toString())
			Tickwatch.LOGGER.info("[Milestone] {}", Text.plain(message))
			try {
				broadcast(message)
			} catch (e: Exception) {
				Tickwatch.LOGGER.error("Milestone broadcast failed: {}", e.toString())
			}
		}
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

	private fun resetLuckPermsConnection() {
		luckPermsAttempts = if (config.luckPermsEnabled && LuckPermsBridge.present) 0 else LUCKPERMS_MAX_ATTEMPTS
		luckPermsTickCounter = 0
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

		// Playtime and sessions change every tick, so they're pushed on a slower timer than kills/deaths.
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
