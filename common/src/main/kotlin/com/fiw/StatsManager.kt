package com.fiw

import java.nio.file.Path
import java.util.UUID

/**
 * Owns the in-memory stats and the tracking logic, independent of any mod loader.
 *
 * Every method here is expected to be called from the server thread (the platform
 * event handlers all run there), so [stats] needs no synchronisation. Only disk I/O
 * is pushed off-thread – see [StatsStorage].
 */
object StatsManager {

	/** Autosave cadence. 6000 ticks = 5 minutes at 20 TPS. */
	private const val AUTOSAVE_INTERVAL_TICKS = 6000

	private val stats = HashMap<UUID, PlayerStats>()
	private var storage: StatsStorage? = null
	private var tickCounter = 0
	private var dirty = false

	/** Called when the server starts. [directory] is the per-world Tickwatch folder. */
	fun start(directory: Path) {
		val store = StatsStorage(directory.resolve("stats.json"))
		storage = store
		stats.clear()
		stats.putAll(store.load())
		tickCounter = 0
		dirty = false
		Tickwatch.LOGGER.info("Loaded stats for {} player(s)", stats.size)
	}

	/** Call once per online player, every server tick. */
	fun tickPlayer(uuid: UUID, name: String) {
		entryFor(uuid, name).playTimeTicks++
		dirty = true
	}

	/** Call once after all players have been ticked; handles the autosave cadence. */
	fun afterServerTick() {
		if (++tickCounter >= AUTOSAVE_INTERVAL_TICKS) {
			tickCounter = 0
			if (dirty) {
				flushAsync()
				dirty = false
			}
		}
	}

	fun onPlayerJoin(uuid: UUID, name: String) {
		// Refresh the cached name in case the player changed it, creating the entry if new.
		entryFor(uuid, name).name = name
	}

	fun onPlayerQuit(uuid: UUID, name: String) {
		entryFor(uuid, name).name = name
		// Persist promptly so a crash right after someone logs off can't lose their session.
		flushAsync()
		dirty = false
	}

	fun recordKill(uuid: UUID, name: String) {
		entryFor(uuid, name).kills++
		dirty = true
	}

	fun recordDeath(uuid: UUID, name: String) {
		entryFor(uuid, name).deaths++
		dirty = true
	}

	/** Called when the server is stopping: blocking final save, then tear down. */
	fun stop() {
		val store = storage ?: return
		store.saveBlocking(snapshot())
		store.shutdown()
		storage = null
		stats.clear()
		dirty = false
		Tickwatch.LOGGER.info("Stats saved on shutdown")
	}

	private fun entryFor(uuid: UUID, name: String): PlayerStats =
		stats.getOrPut(uuid) { PlayerStats(name) }

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
