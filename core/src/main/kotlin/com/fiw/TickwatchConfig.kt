package com.fiw

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Settings read from `<world>/fiw-clock/config.json`. A missing file is created with the
 * defaults; a broken file is left untouched (so the admin's edits aren't lost) and the
 * defaults are used for this run instead.
 */
class TickwatchConfig(
	/** Mirror stats into LuckPerms meta when LuckPerms is installed. */
	val luckPermsEnabled: Boolean = true,
	/** Prefix for every meta key, e.g. `tickwatch_kills`. */
	val metaPrefix: String = "tickwatch_",
	/** How often playtime (which changes every tick) is pushed to LuckPerms. */
	val playtimeSyncSeconds: Int = 60,
	/** Which stats are mirrored into LuckPerms meta. */
	val metaStats: List<Stat> = Stat.entries.toList(),
	/** Detect AFK players: their time counts as AFK time instead of playtime. */
	val afkEnabled: Boolean = true,
	/** Minutes without turning the camera before a player counts as AFK. */
	val afkMinutes: Int = 5,
	/** Permission level for `/tickwatch stats` and `top` when LuckPerms doesn't decide (0 = everyone). */
	val commandPermissionLevel: Int = 0,
	/** Broadcast a chat message when a player reaches one of [milestones]. */
	val milestonesEnabled: Boolean = false,
	val milestones: List<Milestone> = Milestone.DEFAULTS,
) {
	/** Announce [message] when [stat] reaches any value in [at]. */
	class Milestone(val stat: Stat, val at: Set<Long>, val message: String) {
		companion object {
			/** Stats that only ever grow by one, so "reached" is simply "equals". */
			@JvmField
			val SUPPORTED: Set<Stat> = setOf(
				Stat.PLAYTIME_HOURS, Stat.KILLS, Stat.PLAYER_KILLS, Stat.MOB_KILLS,
				Stat.DEATHS, Stat.KILL_STREAK, Stat.JOINS,
			)

			@JvmField
			val DEFAULTS: List<Milestone> = listOf(
				Milestone(Stat.PLAYTIME_HOURS, levels(1, 10, 50, 100, 250, 500, 1000), "&6{player} &ehas played for &6{value} &ehours!"),
				Milestone(Stat.KILLS, levels(10, 50, 100, 250, 500, 1000), "&6{player} &ereached &6{value} &ekills!"),
				Milestone(Stat.KILL_STREAK, levels(5, 10, 25, 50), "&6{player} &eis on a &6{value} &ekill streak!"),
				Milestone(Stat.JOINS, levels(10, 100, 500, 1000), "&6{player} &ehas joined &6{value} &etimes!"),
			)

			private fun levels(vararg values: Long): Set<Long> = values.toSortedSet()
		}
	}

	companion object {
		private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

		/** Loads [file], writing the defaults if it doesn't exist; falls back to the defaults if it's broken. */
		@JvmStatic
		fun load(file: Path): TickwatchConfig = tryLoad(file) ?: TickwatchConfig()

		/** Like [load], but returns null (after logging why) when [file] is broken. */
		@JvmStatic
		fun tryLoad(file: Path): TickwatchConfig? {
			if (!Files.exists(file)) {
				val defaults = TickwatchConfig()
				try {
					Files.createDirectories(file.parent)
					Files.writeString(file, gson.toJson(defaults.toJson()))
				} catch (e: Exception) {
					Tickwatch.LOGGER.warn("Could not write default config: {}", e.toString())
				}
				return defaults
			}
			return try {
				fromJson(JsonParser.parseString(Files.readString(file)).asJsonObject)
			} catch (e: Exception) {
				Tickwatch.LOGGER.error("Invalid {}, using defaults: {}", file.fileName, e.toString())
				null
			}
		}

		internal fun fromJson(root: JsonObject): TickwatchConfig {
			val defaults = TickwatchConfig()
			val lp = root.getAsJsonObject("luckperms") ?: JsonObject()
			val stats = lp.getAsJsonArray("stats")?.mapNotNull { element ->
				Stat.byId(element.asString).also {
					if (it == null) Tickwatch.LOGGER.warn("Unknown stat '{}' in config, ignoring", element.asString)
				}
			}
			val afk = root.getAsJsonObject("afk") ?: JsonObject()
			val commands = root.getAsJsonObject("commands") ?: JsonObject()
			val milestones = root.getAsJsonObject("milestones") ?: JsonObject()
			return TickwatchConfig(
				luckPermsEnabled = lp.get("enabled")?.asBoolean ?: defaults.luckPermsEnabled,
				metaPrefix = lp.get("metaPrefix")?.asString ?: defaults.metaPrefix,
				playtimeSyncSeconds = (lp.get("playtimeSyncSeconds")?.asInt ?: defaults.playtimeSyncSeconds).coerceAtLeast(1),
				metaStats = stats ?: defaults.metaStats,
				afkEnabled = afk.get("enabled")?.asBoolean ?: defaults.afkEnabled,
				afkMinutes = (afk.get("minutes")?.asInt ?: defaults.afkMinutes).coerceAtLeast(1),
				commandPermissionLevel = (commands.get("permissionLevel")?.asInt ?: defaults.commandPermissionLevel).coerceIn(0, 4),
				milestonesEnabled = milestones.get("enabled")?.asBoolean ?: defaults.milestonesEnabled,
				milestones = milestones.getAsJsonArray("rules")?.mapNotNull(::milestoneFromJson) ?: defaults.milestones,
			)
		}

		private fun milestoneFromJson(element: JsonElement): Milestone? {
			val obj = element.asJsonObject
			val id = obj.get("stat")?.asString
			val stat = id?.let(Stat::byId)
			if (stat == null || stat !in Milestone.SUPPORTED) {
				Tickwatch.LOGGER.warn(
					"Milestones can't use stat '{}', ignoring (supported: {})",
					id, Milestone.SUPPORTED.joinToString { it.id },
				)
				return null
			}
			val at = obj.getAsJsonArray("at")?.map { it.asLong }?.toSortedSet() ?: sortedSetOf()
			val message = obj.get("message")?.asString ?: "&6{player} &ereached &6{value} &e${stat.label.lowercase()}!"
			return Milestone(stat, at, message)
		}
	}

	internal fun toJson(): JsonObject {
		val lp = JsonObject()
		lp.addProperty("enabled", luckPermsEnabled)
		lp.addProperty("metaPrefix", metaPrefix)
		lp.addProperty("playtimeSyncSeconds", playtimeSyncSeconds)
		lp.add("stats", JsonArray().apply { metaStats.forEach { add(it.id) } })

		val afk = JsonObject()
		afk.addProperty("enabled", afkEnabled)
		afk.addProperty("minutes", afkMinutes)

		val commands = JsonObject()
		commands.addProperty("permissionLevel", commandPermissionLevel)

		val rules = JsonArray()
		for (milestone in milestones) {
			val rule = JsonObject()
			rule.addProperty("stat", milestone.stat.id)
			rule.add("at", JsonArray().apply { milestone.at.forEach { add(it) } })
			rule.addProperty("message", milestone.message)
			rules.add(rule)
		}
		val milestoneRoot = JsonObject()
		milestoneRoot.addProperty("enabled", milestonesEnabled)
		milestoneRoot.add("rules", rules)

		val root = JsonObject()
		root.add("luckperms", lp)
		root.add("afk", afk)
		root.add("commands", commands)
		root.add("milestones", milestoneRoot)
		return root
	}
}
