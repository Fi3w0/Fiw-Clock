package com.fiw

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Shared constants and logger for Tickwatch.
 *
 * This whole `core` module is deliberately Minecraft-free: the platform modules
 * (Fabric / NeoForge / Forge) translate their own server events into plain calls on
 * [StatsManager], so the loader-agnostic logic never touches a loader-specific API.
 */
object Tickwatch {
	/** Fabric mod id, and the name of the per-world data folder on every loader. */
	const val MOD_ID: String = "fiw-clock"
	/** NeoForge / Forge mod id: those loaders don't allow '-' in mod ids. */
	const val FORGE_MOD_ID: String = "fiw_clock"
	const val MOD_NAME: String = "Tickwatch"

	val LOGGER: Logger = LoggerFactory.getLogger(MOD_NAME)
}
