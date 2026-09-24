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
}
