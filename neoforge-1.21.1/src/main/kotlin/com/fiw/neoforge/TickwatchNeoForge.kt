package com.fiw.neoforge

import com.fiw.Tickwatch
import com.fiw.mc.TickwatchServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * NeoForge entry point. Registers itself on the game event bus and forwards NeoForge
 * server events to the shared [TickwatchServer]. Purely server-side – no client code is registered.
 */
@Mod(Tickwatch.FORGE_MOD_ID)
object TickwatchNeoForge {
	init {
		NeoForge.EVENT_BUS.register(this)
		Tickwatch.LOGGER.info("{} initialised (NeoForge, server-side)", Tickwatch.MOD_NAME)
	}

	@SubscribeEvent
	fun onServerStarting(event: ServerStartingEvent) {
		TickwatchServer.onServerStarting(event.server)
	}

	@SubscribeEvent
	fun onServerStopping(event: ServerStoppingEvent) {
		TickwatchServer.onServerStopping()
	}

	@SubscribeEvent
	fun onServerTickPost(event: ServerTickEvent.Post) {
		TickwatchServer.onServerTick(event.server)
	}

	@SubscribeEvent
	fun onPlayerLogin(event: PlayerEvent.PlayerLoggedInEvent) {
		val player = event.entity as? ServerPlayer ?: return
		TickwatchServer.onPlayerJoin(player)
	}

	@SubscribeEvent
	fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
		val player = event.entity as? ServerPlayer ?: return
		TickwatchServer.onPlayerQuit(player)
	}

	@SubscribeEvent
	fun onLivingDeath(event: LivingDeathEvent) {
		TickwatchServer.onLivingDeath(event.entity, event.source)
	}

	@SubscribeEvent
	fun onRegisterCommands(event: RegisterCommandsEvent) {
		TickwatchServer.registerCommands(event.dispatcher)
	}
}
