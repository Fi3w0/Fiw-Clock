package com.fiw

/**
 * Mutable, in-memory stats for a single player. Lives on the server thread only;
 * a deep copy is taken before anything is handed to the I/O thread.
 */
class PlayerStats(
	var name: String,
	var playTimeTicks: Long = 0L,
	var kills: Long = 0L,
	var deaths: Long = 0L,
) {
	fun copy(): PlayerStats = PlayerStats(name, playTimeTicks, kills, deaths)
}