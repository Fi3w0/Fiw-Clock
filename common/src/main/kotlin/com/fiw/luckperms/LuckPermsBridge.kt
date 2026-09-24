package com.fiw.luckperms

import com.fiw.PlayerStats
import com.fiw.Tickwatch
import com.fiw.TickwatchConfig
import java.util.UUID

/**
 * LuckPerms-free view of the LuckPerms integration. Nothing in this file references a
 * LuckPerms class, so it is always safe to load; [LuckPermsSync] (which does) is only
 * touched after [connect] has confirmed the API is on the classpath.
 */
interface LuckPermsBridge {
	/** Mirror [stats] into the player's transient LuckPerms meta. No-op if the user isn't loaded. */
	fun push(uuid: UUID, stats: PlayerStats)

	/** LuckPerms' verdict on [node] for an online player, or null when it has no opinion. */
	fun checkPermission(uuid: UUID, node: String): Boolean?

	companion object {
		private const val API_CLASS = "net.luckperms.api.LuckPermsProvider"

		/** True when the LuckPerms API is installed at all (checked once). */
		val present: Boolean by lazy {
			try {
				Class.forName(API_CLASS, false, LuckPermsBridge::class.java.classLoader)
				true
			} catch (e: ClassNotFoundException) {
				false
			} catch (e: LinkageError) {
				false
			}
		}

		/**
		 * Returns a live bridge, or null if LuckPerms is absent or hasn't finished enabling
		 * yet (callers retry later in the second case).
		 */
		fun connect(config: TickwatchConfig): LuckPermsBridge? {
			if (!present) return null
			return try {
				LuckPermsSync.create(config)
			} catch (e: IllegalStateException) {
				// LuckPermsProvider.get() before LuckPerms has enabled.
				null
			} catch (e: LinkageError) {
				Tickwatch.LOGGER.warn("Incompatible LuckPerms API, integration disabled: {}", e.toString())
				null
			}
		}
	}
}
