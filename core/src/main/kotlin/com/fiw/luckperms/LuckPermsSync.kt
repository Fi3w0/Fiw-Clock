package com.fiw.luckperms

import com.fiw.PlayerStats
import com.fiw.TickwatchConfig
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.MetaNode
import net.luckperms.api.util.Tristate
import java.util.UUID

/**
 * Writes stats as LuckPerms meta, e.g. `tickwatch_kills=12`, so anything that reads
 * LuckPerms meta (chat/tab formatters, `/lp user <name> meta info`, other mods) can show them.
 *
 * Meta goes into **transient** data: it lives only while the user is loaded, is never
 * written to the LuckPerms database, and is rebuilt from Tickwatch on every login.
 * Only keys whose value actually changed are touched, to avoid needless cache rebuilds;
 * keys under the prefix that are no longer configured are removed.
 */
internal class LuckPermsSync private constructor(
	private val luckPerms: LuckPerms,
	private val config: TickwatchConfig,
) : LuckPermsBridge {

	override fun push(uuid: UUID, stats: PlayerStats) {
		val user = luckPerms.userManager.getUser(uuid) ?: return
		val data = user.transientData()

		val current = HashMap<String, String>()
		for (node in data.toCollection()) {
			if (node is MetaNode && node.metaKey.startsWith(config.metaPrefix)) {
				current[node.metaKey] = node.metaValue
			}
		}

		val wanted = LinkedHashMap<String, String>()
		for (stat in config.metaStats) {
			wanted[config.metaPrefix + stat.id] = stat.format(stats)
		}

		// Keys this prefix owns but that are no longer configured.
		for (key in current.keys) {
			if (key !in wanted) data.clear(NodeType.META.predicate { it.metaKey == key })
		}
		for ((key, value) in wanted) {
			if (current[key] == value) continue
			data.clear(NodeType.META.predicate { it.metaKey == key })
			data.add(MetaNode.builder(key, value).build())
		}
	}

	override fun clear(uuid: UUID) {
		val user = luckPerms.userManager.getUser(uuid) ?: return
		user.transientData().clear(NodeType.META.predicate { it.metaKey.startsWith(config.metaPrefix) })
	}

	override fun checkPermission(uuid: UUID, node: String): Boolean? {
		val user = luckPerms.userManager.getUser(uuid) ?: return null
		return when (val result = user.cachedData.permissionData.checkPermission(node)) {
			Tristate.UNDEFINED -> null
			else -> result.asBoolean()
		}
	}

	companion object {
		/** @throws IllegalStateException if LuckPerms is installed but not enabled yet. */
		fun create(config: TickwatchConfig): LuckPermsBridge = LuckPermsSync(LuckPermsProvider.get(), config)
	}
}
