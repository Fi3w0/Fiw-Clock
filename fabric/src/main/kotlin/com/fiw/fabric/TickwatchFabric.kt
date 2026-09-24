package com.fiw.fabric

import com.fiw.StatsManager
import com.fiw.Tickwatch
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource

/**
 * Fabric entry point. Translates Fabric API server events into calls on the
 * loader-agnostic [StatsManager]. Purely server-side – no client code is registered.
 */
class TickwatchFabric : ModInitializer {
	override fun onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register { server ->
			val dir = server.getWorldPath(LevelResource.ROOT).resolve(Tickwatch.MOD_ID)
			StatsManager.start(dir)
		}

		ServerLifecycleEvents.SERVER_STOPPING.register {
			StatsManager.stop()
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			for (player in server.playerList.players) {
				StatsManager.tickPlayer(player.uuid, player.gameProfile.name)
			}
			StatsManager.afterServerTick()
		}

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			val player = handler.player
			StatsManager.onPlayerJoin(player.uuid, player.gameProfile.name)
		}

		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			val player = handler.player
			StatsManager.onPlayerQuit(player.uuid, player.gameProfile.name)
		}

		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			if (entity is ServerPlayer) {
				StatsManager.recordDeath(entity.uuid, entity.gameProfile.name)
			}
			val attacker = source.entity
			if (attacker is ServerPlayer) {
				StatsManager.recordKill(attacker.uuid, attacker.gameProfile.name, entity.uuid, entity is ServerPlayer)
			}
		}

		Tickwatch.LOGGER.info("{} initialised (Fabric, server-side)", Tickwatch.MOD_NAME)
	}
}
