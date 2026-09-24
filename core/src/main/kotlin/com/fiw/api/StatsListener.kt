package com.fiw.api

/**
 * Notified when a player's stats change for a discrete reason. Playtime ticks do not fire
 * events (that would be every tick); read [StatsSnapshot.playTimeTicks] when you need it.
 *
 * Always invoked on the server thread. Keep it fast – exceptions are caught and logged.
 */
fun interface StatsListener {
	fun onStatsChanged(snapshot: StatsSnapshot, reason: Reason)

	enum class Reason {
		JOIN,
		QUIT,
		PLAYER_KILL,
		MOB_KILL,
		DEATH,
		/** The player stopped turning their camera for `afk.minutes` and is now AFK. */
		AFK_START,
		/** An AFK player looked around again. */
		AFK_END,
	}
}
