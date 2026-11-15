package com.fiw.integration

import com.fiw.Tickwatch
import java.lang.reflect.Method
import java.util.UUID

/** Optional integration: loading Tickwatch never requires Admin Tools to be installed. */
internal object AdminAfkBridge {
	private val query: Method? = try {
		Class.forName("com.fiw.admin.api.AdminAfkApi")
			.getMethod("isAfk", UUID::class.java)
	} catch (_: ClassNotFoundException) {
		null
	} catch (_: NoSuchMethodException) {
		null
	} catch (_: LinkageError) {
		null
	}
	private var failed = false

	/** Null means Admin Tools has no running AFK authority for this player. */
	fun state(uuid: UUID): Boolean? {
		val method = query ?: return null
		if (failed) return null
		return try {
			method.invoke(null, uuid) as Boolean?
		} catch (e: ReflectiveOperationException) {
			failed = true
			Tickwatch.LOGGER.error("Admin Tools AFK integration failed; using local detection: {}", e.toString())
			null
		}
	}
}
