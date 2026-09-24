package com.fiw

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class TickwatchConfigTest {

	@TempDir
	lateinit var dir: Path

	@Test
	fun `missing config is created with defaults`() {
		val file = dir.resolve("config.json")
		val config = TickwatchConfig.load(file)
		assertTrue(Files.exists(file))
		assertTrue(config.luckPermsEnabled)
		assertEquals("tickwatch_", config.metaPrefix)
		assertEquals(Stat.entries.toList(), config.metaStats)
		// Round-trips through its own output.
		assertEquals(config.metaStats, TickwatchConfig.load(file).metaStats)
	}

	@Test
	fun `partial config keeps defaults and skips unknown stats`() {
		val file = dir.resolve("config.json")
		Files.writeString(file, """{"luckperms":{"enabled":false,"stats":["kills","nope","DEATHS"],"playtimeSyncSeconds":0}}""")
		val config = TickwatchConfig.load(file)
		assertFalse(config.luckPermsEnabled)
		assertEquals("tickwatch_", config.metaPrefix)
		assertEquals(1, config.playtimeSyncSeconds)
		assertEquals(listOf(Stat.KILLS, Stat.DEATHS), config.metaStats)
	}

	@Test
	fun `broken config falls back to defaults without overwriting`() {
		val file = dir.resolve("config.json")
		Files.writeString(file, "{ not json")
		val config = TickwatchConfig.load(file)
		assertTrue(config.luckPermsEnabled)
		assertEquals("{ not json", Files.readString(file))
	}

	@Test
	fun `new sections parse and round-trip`() {
		val file = dir.resolve("config.json")
		val defaults = TickwatchConfig.load(file)
		assertTrue(defaults.afkEnabled)
		assertEquals(5, defaults.afkMinutes)
		assertEquals(0, defaults.commandPermissionLevel)
		assertFalse(defaults.milestonesEnabled)
		// '&' codes must survive being written by Gson.
		assertTrue(Files.readString(file).contains("&6{player}"))
		assertEquals(defaults.milestones.map { it.message }, TickwatchConfig.load(file).milestones.map { it.message })

		Files.writeString(
			file,
			"""{"afk":{"minutes":0},"commands":{"permissionLevel":9},
			"milestones":{"enabled":true,"rules":[{"stat":"deaths","at":[3,1]},{"stat":"kdr","at":[1]}]}}""",
		)
		val config = TickwatchConfig.load(file)
		assertEquals(1, config.afkMinutes)
		assertEquals(4, config.commandPermissionLevel)
		assertTrue(config.milestonesEnabled)
		assertEquals(1, config.milestones.size)
		assertEquals(Stat.DEATHS, config.milestones[0].stat)
		assertEquals(listOf(1L, 3L), config.milestones[0].at.toList())
	}

	@Test
	fun `tryLoad returns null for a broken file`() {
		val file = dir.resolve("config.json")
		Files.writeString(file, "[]")
		kotlin.test.assertNull(TickwatchConfig.tryLoad(file))
	}
}
