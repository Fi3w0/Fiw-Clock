package com.fiw

/**
 * Mutable, in-memory stats for a single player. Only ever mutated on the server thread;
 * a deep copy is taken before anything is handed to the I/O thread or to API consumers.
 *
 * [kills] is the all-time total. [playerKills] + [mobKills] can be lower than it for data
 * recorded before 1.1.0, when kills were not split by victim type.
 */
class PlayerStats(
	var name: String,
	/** Active (non-AFK) time played, in ticks. */
	var playTimeTicks: Long = 0L,
	/** Time spent online but AFK, in ticks. */
	var afkTicks: Long = 0L,
	var kills: Long = 0L,
	var playerKills: Long = 0L,
	var mobKills: Long = 0L,
	var deaths: Long = 0L,
	var killStreak: Long = 0L,
	var bestKillStreak: Long = 0L,
	var joins: Long = 0L,
	/** Epoch millis of the first recorded join, or 0 if unknown (pre-1.1.0 data). */
	var firstJoin: Long = 0L,
	/** Epoch millis of the last join/quit, or 0 if unknown. */
	var lastSeen: Long = 0L,
	var longestSessionTicks: Long = 0L,
	// Live state below: never saved, reset on every join/quit.
	/** Ticks online since the current login (AFK included); 0 while offline. */
	var sessionTicks: Long = 0L,
	var online: Boolean = false,
	var afk: Boolean = false,
) {
	/** Kills per death; equals [kills] when the player has never died. */
	val kdr: Double
		get() = if (deaths == 0L) kills.toDouble() else kills.toDouble() / deaths

	fun copy(): PlayerStats = PlayerStats(
		name, playTimeTicks, afkTicks, kills, playerKills, mobKills, deaths,
		killStreak, bestKillStreak, joins, firstJoin, lastSeen, longestSessionTicks,
		sessionTicks, online, afk,
	)
}
