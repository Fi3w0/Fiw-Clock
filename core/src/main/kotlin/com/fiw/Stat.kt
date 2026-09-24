package com.fiw

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Every stat Tickwatch exposes to the outside world (LuckPerms meta, the public API,
 * leaderboards). [id] is the stable, lowercase key used in meta keys and configs.
 */
enum class Stat(val id: String, private val raw: (PlayerStats) -> Number) {
	PLAYTIME("playtime", { it.playTimeTicks }),
	PLAYTIME_HOURS("playtime_hours", { it.playTimeTicks / TICKS_PER_HOUR }),
	KILLS("kills", { it.kills }),
	PLAYER_KILLS("player_kills", { it.playerKills }),
	MOB_KILLS("mob_kills", { it.mobKills }),
	DEATHS("deaths", { it.deaths }),
	KDR("kdr", { it.kdr }),
	KILL_STREAK("kill_streak", { it.killStreak }),
	BEST_KILL_STREAK("best_kill_streak", { it.bestKillStreak }),
	JOINS("joins", { it.joins }),
	FIRST_JOIN("first_join", { it.firstJoin }),
	LAST_SEEN("last_seen", { it.lastSeen });

	/** Numeric value, used for sorting leaderboards. */
	fun value(stats: PlayerStats): Double = raw(stats).toDouble()

	/** Human-readable value, used for display (LuckPerms meta, chat output). */
	fun format(stats: PlayerStats): String = when (this) {
		PLAYTIME -> formatTicks(stats.playTimeTicks)
		KDR -> "%.2f".format(java.util.Locale.ROOT, stats.kdr)
		FIRST_JOIN -> formatDate(stats.firstJoin)
		LAST_SEEN -> formatDate(stats.lastSeen)
		else -> raw(stats).toString()
	}

	companion object {
		private const val TICKS_PER_HOUR = 20L * 60 * 60
		private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

		@JvmStatic
		fun byId(id: String): Stat? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

		@JvmStatic
		fun formatTicks(ticks: Long): String {
			val totalSeconds = ticks / 20
			val hours = totalSeconds / 3600
			val minutes = (totalSeconds % 3600) / 60
			val seconds = totalSeconds % 60
			return "%dh %02dm %02ds".format(hours, minutes, seconds)
		}

		@JvmStatic
		fun formatDate(epochMillis: Long): String =
			if (epochMillis <= 0L) "unknown" else DATE.format(Instant.ofEpochMilli(epochMillis))
	}
}
