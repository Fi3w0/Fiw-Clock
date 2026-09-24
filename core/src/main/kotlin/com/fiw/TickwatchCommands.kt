package com.fiw

import com.fiw.api.StatsSnapshot
import com.fiw.api.TickwatchApi
import java.util.UUID

/**
 * The Minecraft-free half of `/tickwatch`: permission nodes and every reply. The
 * per-version Minecraft code only parses arguments, checks permissions and turns the
 * `&`-formatted lines (see [Text]) into chat components.
 */
object TickwatchCommands {
	const val ROOT = "tickwatch"

	const val PERMISSION_STATS = "tickwatch.command.stats"
	const val PERMISSION_STATS_OTHERS = "tickwatch.command.stats.others"
	const val PERMISSION_TOP = "tickwatch.command.top"
	const val PERMISSION_RELOAD = "tickwatch.command.reload"

	/** Vanilla permission level for `reload` when LuckPerms doesn't decide (2 = ops). */
	const val RELOAD_LEVEL = 2
	const val DEFAULT_TOP = 10
	const val MAX_TOP = 50

	class Reply(val lines: List<String>, val success: Boolean)

	/** Vanilla permission level required for [node] when LuckPerms has no opinion on it. */
	@JvmStatic
	fun defaultLevel(node: String): Int =
		if (node == PERMISSION_RELOAD) RELOAD_LEVEL else StatsManager.config.commandPermissionLevel

	/** Ids offered for `/tickwatch top <stat>`. */
	@JvmStatic
	fun rankableStatIds(): List<String> = Stat.rankable().map { it.id }

	/** Every known player name, for tab completion. */
	@JvmStatic
	fun knownNames(): List<String> = TickwatchApi.all().map { it.name }.sortedBy { it.lowercase() }

	@JvmStatic
	fun help(): Reply = Reply(
		listOf(
			"&6&l» &eTickwatch",
			"&6/$ROOT stats &7[player] &8- &fplaytime, kills, deaths and more",
			"&6/$ROOT top &7<stat> [count] &8- &fleaderboard",
			"&6/$ROOT reload &8- &freload config.json",
			"&7Stats: &f" + rankableStatIds().joinToString(", "),
		),
		success = true,
	)

	/** Stats of the player running the command. */
	@JvmStatic
	fun statsOf(uuid: UUID): Reply =
		TickwatchApi.get(uuid)?.let { Reply(statsLines(it), success = true) }
			?: failure("&cNo stats recorded for you yet.")

	/** Stats of any player Tickwatch has seen, by (case-insensitive) name. */
	@JvmStatic
	fun statsOf(name: String): Reply =
		TickwatchApi.getByName(name)?.let { Reply(statsLines(it), success = true) }
			?: failure("&cNo stats recorded for '$name'.")

	@JvmStatic
	fun top(statId: String, count: Int): Reply {
		val stat = Stat.byId(statId)?.takeIf { it.rankable }
			?: return failure("&cUnknown stat '$statId'. &7Try: &f" + rankableStatIds().joinToString(", "))
		val entries = TickwatchApi.top(stat, count.coerceIn(1, MAX_TOP))
		if (entries.isEmpty()) return failure("&cNo stats recorded yet.")
		val lines = ArrayList<String>(entries.size + 1)
		lines.add("&6&l» &eTop ${entries.size} &7- &e${stat.label}")
		entries.forEachIndexed { index, snapshot ->
			lines.add("&6${index + 1}. &f${snapshot.name} &7${snapshot.format(stat)}")
		}
		return Reply(lines, success = true)
	}

	@JvmStatic
	fun reload(): Reply =
		if (StatsManager.reload()) Reply(listOf("&aTickwatch config reloaded."), success = true)
		else failure("&cconfig.json is invalid; kept the current settings (see the server log).")

	internal fun statsLines(s: StatsSnapshot): List<String> {
		val status = when {
			s.isAfk -> "&eAFK"
			s.isOnline -> "&aonline"
			else -> "&7offline"
		}
		val lines = arrayListOf(
			"&6&l» &e${s.name} &8(${status}&8)",
			"&7Playtime: &f${s.format(Stat.PLAYTIME)} &8(AFK ${s.format(Stat.AFK_TIME)})",
			"&7Kills: &f${s.kills} &8(${s.playerKills} players, ${s.mobKills} mobs)",
			"&7Deaths: &f${s.deaths} &8| &7K/D: &f${s.format(Stat.KDR)}",
			"&7Kill streak: &f${s.killStreak} &8(best ${s.bestKillStreak})",
		)
		if (s.isOnline) {
			lines.add("&7Session: &f${s.format(Stat.SESSION)} &8(longest ${s.format(Stat.LONGEST_SESSION)})")
		} else {
			lines.add("&7Longest session: &f${s.format(Stat.LONGEST_SESSION)}")
		}
		lines.add("&7Joins: &f${s.joins} &8| &7First join: &f${s.format(Stat.FIRST_JOIN)} &8| &7Last seen: &f${s.format(Stat.LAST_SEEN)}")
		return lines
	}

	private fun failure(line: String) = Reply(listOf(line), success = false)
}
