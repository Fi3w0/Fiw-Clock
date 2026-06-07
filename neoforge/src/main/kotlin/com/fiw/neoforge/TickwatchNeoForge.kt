package com.fiw.neoforge

import com.fiw.StatsManager
import com.fiw.Tickwatch
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * NeoForge entry point. Registers itself on the game event bus and translates
 * NeoForge server events into calls on the loader-agnostic [StatsManager].
 * Purely server-side – no client code is registered.
 */
@Mod(Tickwatch.MOD_ID)
object TickwatchNeoForge {
	init {
		NeoForge.EVENT_BUS.register(this)
		Tickwatch.LOGGER.info("{} initialised (NeoForge, server-side)", Tickwatch.MOD_NAME)
	}

	@SubscribeEvent
	fun onServerStarting(event: ServerStartingEvent) {
		val dir = event.server.getWorldPath(LevelResource.ROOT).resolve(Tickwatch.MOD_ID)
		StatsManager.start(dir)
	}

	@SubscribeEvent
	fun onServerStopping(event: ServerStoppingEvent) {
		StatsManager.stop()
	}

	@SubscribeEvent
	fun onServerTickPost(event: ServerTickEvent.Post) {
		for (player in event.server.playerList.players) {
			StatsManager.tickPlayer(player.uuid, player.gameProfile.name)
		}
		StatsManager.afterServerTick()
	}

	@SubscribeEvent
	fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
		val player = event.entity as? ServerPlayer ?: return
		StatsManager.onPlayerJoin(player.uuid, player.gameProfile.name)
	}

	@SubscribeEvent
	fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
		val player = event.entity as? ServerPlayer ?: return
		StatsManager.onPlayerQuit(player.uuid, player.gameProfile.name)
	}

	@SubscribeEvent
	fun onLivingDeath(event: LivingDeathEvent) {
		val victim = event.entity
		if (victim is ServerPlayer) {
			StatsManager.recordDeath(victim.uuid, victim.gameProfile.name)
		}
		val attacker = event.source.entity
		if (attacker is ServerPlayer) {
			StatsManager.recordKill(attacker.uuid, attacker.gameProfile.name)
		}
	}
}
