package com.fiw.api

import com.fiw.PlayerStats
import com.fiw.Stat
import java.util.UUID

/**
 * Immutable copy of one player's stats at the moment it was taken. Safe to keep and to
 * pass between threads; it never changes after creation.
 */
class StatsSnapshot internal constructor(
	val uuid: UUID,
	private val stats: PlayerStats,
) {
	val name: String get() = stats.name
	val playTimeTicks: Long get() = stats.playTimeTicks
	val kills: Long get() = stats.kills
	val playerKills: Long get() = stats.playerKills
	val mobKills: Long get() = stats.mobKills
	val deaths: Long get() = stats.deaths
	val kdr: Double get() = stats.kdr
	val killStreak: Long get() = stats.killStreak
	val bestKillStreak: Long get() = stats.bestKillStreak
	val joins: Long get() = stats.joins
	val firstJoin: Long get() = stats.firstJoin
	val lastSeen: Long get() = stats.lastSeen

	/** Numeric value of [stat], e.g. for sorting. */
	fun value(stat: Stat): Double = stat.value(stats)

	/** Display string of [stat], e.g. `2h 14m 30s` or `1.50`. */
	fun format(stat: Stat): String = stat.format(stats)

	override fun toString(): String = "StatsSnapshot($name, $uuid)"
}
