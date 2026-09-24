package com.fiw

import com.fiw.api.StatsListener
import com.fiw.api.TickwatchApi
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class StatsManagerTest {

	@TempDir
	lateinit var dir: Path

	private val alex = UUID.randomUUID()
	private val steve = UUID.randomUUID()
	private val zombie = UUID.randomUUID()

	@BeforeTest
	fun setUp() {
		StatsManager.clock = { 1_000L }
	}

	@AfterTest
	fun tearDown() {
		StatsManager.stop()
		StatsManager.clock = System::currentTimeMillis
	}

	@Test
	fun `kills are split by victim type and streaks reset on death`() {
		StatsManager.start(dir)
		StatsManager.recordKill(alex, "Alex", zombie, victimIsPlayer = false)
		StatsManager.recordKill(alex, "Alex", steve, victimIsPlayer = true)
		StatsManager.recordKill(alex, "Alex", zombie, victimIsPlayer = false)
		StatsManager.recordDeath(alex, "Alex")
		StatsManager.recordKill(alex, "Alex", zombie, victimIsPlayer = false)

		val s = TickwatchApi.get(alex)!!
		assertEquals(4, s.kills)
		assertEquals(1, s.playerKills)
		assertEquals(3, s.mobKills)
		assertEquals(1, s.deaths)
		assertEquals(1, s.killStreak)
		assertEquals(3, s.bestKillStreak)
		assertEquals(4.0, s.kdr)
	}

	@Test
	fun `killing yourself is not a kill`() {
		StatsManager.start(dir)
		StatsManager.recordKill(alex, "Alex", alex, victimIsPlayer = true)
		assertNull(TickwatchApi.get(alex))
	}

	@Test
	fun `joins track first join once and last seen every time`() {
		StatsManager.start(dir)
		StatsManager.onPlayerJoin(alex, "Alex")
		StatsManager.clock = { 5_000L }
		StatsManager.onPlayerQuit(alex, "Alex")
		StatsManager.onPlayerJoin(alex, "Alex2")

		val s = TickwatchApi.getByName("alex2")!!
		assertEquals(2, s.joins)
		assertEquals(1_000L, s.firstJoin)
		assertEquals(5_000L, s.lastSeen)
		assertNull(TickwatchApi.getByName("Alex"))
	}

	@Test
	fun `all stats survive a restart`() {
		StatsManager.start(dir)
		StatsManager.onPlayerJoin(alex, "Alex")
		repeat(40) { StatsManager.tickPlayer(alex, "Alex") }
		StatsManager.recordKill(alex, "Alex", steve, victimIsPlayer = true)
		StatsManager.recordDeath(alex, "Alex")
		StatsManager.stop()

		StatsManager.start(dir)
		val s = TickwatchApi.get(alex)!!
		assertEquals(40, s.playTimeTicks)
		assertEquals(1, s.playerKills)
		assertEquals(1, s.deaths)
		assertEquals(1, s.bestKillStreak)
		assertEquals(1, s.joins)
		assertEquals(1_000L, s.firstJoin)
	}

	@Test
	fun `1_0_0 stats files still load`() {
		Files.writeString(
			dir.resolve("stats.json"),
			"""{"players":{"$alex":{"name":"Alex","playTimeTicks":72000,"kills":7,"deaths":2}}}""",
		)
		StatsManager.start(dir)
		val s = TickwatchApi.get(alex)!!
		assertEquals(7, s.kills)
		assertEquals(0, s.playerKills)
		assertEquals(0, s.mobKills)
		assertEquals(0L, s.firstJoin)
		assertEquals("1h 00m 00s", s.format(Stat.PLAYTIME))
		assertEquals("3.50", s.format(Stat.KDR))
		assertEquals("unknown", s.format(Stat.FIRST_JOIN))
	}

	@Test
	fun `top sorts highest first`() {
		StatsManager.start(dir)
		StatsManager.recordKill(alex, "Alex", zombie, victimIsPlayer = false)
		repeat(3) { StatsManager.recordKill(steve, "Steve", zombie, victimIsPlayer = false) }

		val top = TickwatchApi.top(Stat.MOB_KILLS, 10)
		assertEquals(listOf("Steve", "Alex"), top.map { it.name })
		assertEquals(1, TickwatchApi.top(Stat.MOB_KILLS, 1).size)
	}

	@Test
	fun `listeners see discrete changes`() {
		val seen = mutableListOf<StatsListener.Reason>()
		val listener = StatsListener { _, reason -> seen += reason }
		TickwatchApi.addListener(listener)
		try {
			StatsManager.start(dir)
			StatsManager.onPlayerJoin(alex, "Alex")
			StatsManager.tickPlayer(alex, "Alex")
			StatsManager.recordKill(alex, "Alex", steve, victimIsPlayer = true)
			StatsManager.recordKill(alex, "Alex", zombie, victimIsPlayer = false)
			StatsManager.recordDeath(alex, "Alex")
			StatsManager.onPlayerQuit(alex, "Alex")
		} finally {
			TickwatchApi.removeListener(listener)
		}
		assertEquals(
			listOf(
				StatsListener.Reason.JOIN, StatsListener.Reason.PLAYER_KILL,
				StatsListener.Reason.MOB_KILL, StatsListener.Reason.DEATH, StatsListener.Reason.QUIT,
			),
			seen,
		)
	}

	@Test
	fun `running without LuckPerms is a no-op`() {
		StatsManager.start(dir)
		StatsManager.onPlayerJoin(alex, "Alex")
		repeat(200) { StatsManager.afterServerTick() }
		assertTrue(TickwatchApi.isRunning())
		assertNull(TickwatchApi.checkLuckPermsPermission(alex, "tickwatch.command.stats"))
		StatsManager.stop()
		assertFalse(TickwatchApi.isRunning())
	}
}
