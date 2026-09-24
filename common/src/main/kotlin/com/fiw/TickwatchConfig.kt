package com.fiw

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
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
) {
	companion object {
		private val gson = GsonBuilder().setPrettyPrinting().create()

		@JvmStatic
		fun load(file: Path): TickwatchConfig {
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
				TickwatchConfig()
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
			return TickwatchConfig(
				luckPermsEnabled = lp.get("enabled")?.asBoolean ?: defaults.luckPermsEnabled,
				metaPrefix = lp.get("metaPrefix")?.asString ?: defaults.metaPrefix,
				playtimeSyncSeconds = (lp.get("playtimeSyncSeconds")?.asInt ?: defaults.playtimeSyncSeconds).coerceAtLeast(1),
				metaStats = stats ?: defaults.metaStats,
			)
		}
	}

	internal fun toJson(): JsonObject {
		val lp = JsonObject()
		lp.addProperty("enabled", luckPermsEnabled)
		lp.addProperty("metaPrefix", metaPrefix)
		lp.addProperty("playtimeSyncSeconds", playtimeSyncSeconds)
		lp.add("stats", JsonArray().apply { metaStats.forEach { add(it.id) } })
		val root = JsonObject()
		root.add("luckperms", lp)
		return root
	}
}
