package com.fiw.api

import com.fiw.Stat
import com.fiw.StatsManager
import com.fiw.luckperms.LuckPermsBridge
import java.util.UUID

/**
 * Public, stable entry point for other mods. Every method returns immutable snapshots and
 * may be called from any thread; values can lag the server thread by up to one tick.
 *
 * Works the same on every loader and Minecraft version Tickwatch supports.
 */
object TickwatchApi {

	/** False before the server has started or after it has stopped. */
	@JvmStatic
	fun isRunning(): Boolean = StatsManager.isRunning

	@JvmStatic
	fun get(uuid: UUID): StatsSnapshot? = StatsManager.snapshotOf(uuid)

	/** Case-insensitive lookup by the last known username. */
	@JvmStatic
	fun getByName(name: String): StatsSnapshot? = StatsManager.snapshotOf(name)

	@JvmStatic
	fun all(): List<StatsSnapshot> = StatsManager.allSnapshots()

	/** The [limit] best players for [stat], highest first. */
	@JvmStatic
	fun top(stat: Stat, limit: Int): List<StatsSnapshot> = StatsManager.top(stat, limit)

	@JvmStatic
	fun addListener(listener: StatsListener) = StatsManager.addListener(listener)

	@JvmStatic
	fun removeListener(listener: StatsListener) = StatsManager.removeListener(listener)

	/**
	 * LuckPerms' answer for [node] on an online player: true/false, or null when LuckPerms
	 * isn't installed, the user isn't loaded, or the node is unset.
	 */
	@JvmStatic
	fun checkLuckPermsPermission(uuid: UUID, node: String): Boolean? =
		if (LuckPermsBridge.present) StatsManager.luckPerms?.checkPermission(uuid, node) else null
}
