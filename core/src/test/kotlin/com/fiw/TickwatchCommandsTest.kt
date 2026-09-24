package com.fiw

import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class TickwatchCommandsTest {

	@TempDir
	lateinit var dir: Path

	private val alex = UUID.randomUUID()
	private val steve = UUID.randomUUID()

	@AfterTest
	fun tearDown() {
		StatsManager.stop()
	}

	@Test
	fun `stats show an online player's session`() {
		StatsManager.start(dir)
		StatsManager.onPlayerJoin(alex, "Alex")
		StatsManager.recordKill(alex, "Alex", steve, victimIsPlayer = true)

		val reply = TickwatchCommands.statsOf("alex")
		assertTrue(reply.success)
		val text = reply.lines.map(Text::plain)
		assertEquals("» Alex (online)", text[0])
		assertTrue(text.any { it == "Kills: 1 (1 players, 0 mobs)" })
		assertTrue(text.any { it.startsWith("Session: ") })
	}

	@Test
	fun `unknown players and stats fail`() {
		StatsManager.start(dir)
		assertFalse(TickwatchCommands.statsOf("nobody").success)
		assertFalse(TickwatchCommands.statsOf(alex).success)
		assertFalse(TickwatchCommands.top("kills", 10).success)
		StatsManager.recordDeath(alex, "Alex")
		assertFalse(TickwatchCommands.top("name", 10).success)
		assertFalse(TickwatchCommands.top("nope", 10).success)
	}

	@Test
	fun `top lists the leaders`() {
		StatsManager.start(dir)
		repeat(2) { StatsManager.recordDeath(alex, "Alex") }
		StatsManager.recordDeath(steve, "Steve")

		val reply = TickwatchCommands.top("deaths", 1)
		assertTrue(reply.success)
		assertEquals(listOf("» Top 1 - Deaths", "1. Alex 2"), reply.lines.map(Text::plain))
	}

	@Test
	fun `reload reports success`() {
		assertFalse(TickwatchCommands.reload().success)
		StatsManager.start(dir)
		assertTrue(TickwatchCommands.reload().success)
	}
}
