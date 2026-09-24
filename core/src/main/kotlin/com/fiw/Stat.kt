package com.fiw

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every stat Tickwatch exposes to the outside world (LuckPerms meta, the public API,
 * leaderboards, `/tickwatch`). [id] is the stable, lowercase key used in meta keys and configs.
 */
enum class Stat(
	val id: String,
	/** Display name, e.g. in leaderboard headers. */
	val label: String,
	private val raw: (PlayerStats) -> Number,
	/** False for stats that make no sense as a leaderboard (the name, the AFK flag). */
	val rankable: Boolean = true,
) {
	NAME("name", "Name", { 0 }, rankable = false),
	PLAYTIME("playtime", "Playtime", { it.playTimeTicks }),
	PLAYTIME_HOURS("playtime_hours", "Hours played", { it.playTimeTicks / TICKS_PER_HOUR }),
	AFK_TIME("afk_time", "AFK time", { it.afkTicks }),
	KILLS("kills", "Kills", { it.kills }),
	PLAYER_KILLS("player_kills", "Player kills", { it.playerKills }),
	MOB_KILLS("mob_kills", "Mob kills", { it.mobKills }),
	DEATHS("deaths", "Deaths", { it.deaths }),
	KDR("kdr", "K/D ratio", { it.kdr }),
	KILL_STREAK("kill_streak", "Kill streak", { it.killStreak }),
	BEST_KILL_STREAK("best_kill_streak", "Best kill streak", { it.bestKillStreak }),
	JOINS("joins", "Joins", { it.joins }),
	FIRST_JOIN("first_join", "First join", { it.firstJoin }),
	LAST_SEEN("last_seen", "Last seen", { it.lastSeen }),
	SESSION("session", "Current session", { it.sessionTicks }),
	LONGEST_SESSION("longest_session", "Longest session", { it.longestSessionTicks }),
	AFK("afk", "AFK", { if (it.afk) 1 else 0 }, rankable = false);

	/** Numeric value, used for sorting leaderboards. */
	fun value(stats: PlayerStats): Double = raw(stats).toDouble()

	/** Human-readable value, used for display (LuckPerms meta, chat output). */
	fun format(stats: PlayerStats): String = when (this) {
		NAME -> stats.name
		PLAYTIME -> formatTicks(stats.playTimeTicks)
		AFK_TIME -> formatTicks(stats.afkTicks)
		SESSION -> formatTicks(stats.sessionTicks)
		LONGEST_SESSION -> formatTicks(stats.longestSessionTicks)
		KDR -> "%.2f".format(Locale.ROOT, stats.kdr)
		FIRST_JOIN -> formatDate(stats.firstJoin)
		LAST_SEEN -> formatDate(stats.lastSeen)
		AFK -> stats.afk.toString()
		else -> raw(stats).toString()
	}

	companion object {
		internal const val TICKS_PER_MINUTE = 20L * 60
		internal const val TICKS_PER_HOUR = TICKS_PER_MINUTE * 60
		private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

		@JvmStatic
		fun byId(id: String): Stat? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

		/** Stats that can be used for leaderboards. */
		@JvmStatic
		fun rankable(): List<Stat> = entries.filter { it.rankable }

		@JvmStatic
		fun formatTicks(ticks: Long): String {
			val totalSeconds = ticks / 20
			val hours = totalSeconds / 3600
			val minutes = (totalSeconds % 3600) / 60
			val seconds = totalSeconds % 60
			return "%dh %02dm %02ds".format(Locale.ROOT, hours, minutes, seconds)
		}

		@JvmStatic
		fun formatDate(epochMillis: Long): String =
			if (epochMillis <= 0L) "unknown" else DATE.format(Instant.ofEpochMilli(epochMillis))
	}
}
