package com.fiw

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Shared constants and logger for Tickwatch.
 *
 * This whole `common` module is deliberately Minecraft-free: the platform modules
 * (Fabric / NeoForge) translate their own server events into plain calls on
 * [StatsManager], so the loader-agnostic logic never touches a loader-specific API.
 */
object Tickwatch {
	const val MOD_ID: String = "fiw-clock"
	const val MOD_NAME: String = "Tickwatch"

	val LOGGER: Logger = LoggerFactory.getLogger(MOD_NAME)
}
