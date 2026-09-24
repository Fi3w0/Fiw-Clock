package com.fiw.forge

import com.fiw.Tickwatch
import com.fiw.mc.TickwatchServer
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * MinecraftForge entry point. Registers itself on the Forge event bus and forwards Forge
 * server events to the shared [TickwatchServer]. Purely server-side – no client code is registered.
 */
@Mod(Tickwatch.FORGE_MOD_ID)
object TickwatchForge {
	init {
		MinecraftForge.EVENT_BUS.register(this)
		Tickwatch.LOGGER.info("{} initialised (Forge, server-side)", Tickwatch.MOD_NAME)
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
	fun onServerTick(event: TickEvent.ServerTickEvent) {
		// Forge fires this twice per tick; count once, after the world has ticked.
		if (event.phase == TickEvent.Phase.END) {
			TickwatchServer.onServerTick(event.server)
		}
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
