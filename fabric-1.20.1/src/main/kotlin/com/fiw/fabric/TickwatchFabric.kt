package com.fiw.fabric

import com.fiw.Tickwatch
import com.fiw.mc.TickwatchServer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

/**
 * Fabric entry point. Forwards Fabric API server events to the shared [TickwatchServer].
 * Purely server-side – no client code is registered.
 */
class TickwatchFabric : ModInitializer {
	override fun onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register { server ->
			TickwatchServer.onServerStarting(server)
		}

		ServerLifecycleEvents.SERVER_STOPPING.register {
			TickwatchServer.onServerStopping()
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			TickwatchServer.onServerTick(server)
		}

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			TickwatchServer.onPlayerJoin(handler.player)
		}

		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			TickwatchServer.onPlayerQuit(handler.player)
		}

		ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
			TickwatchServer.onLivingDeath(entity, source)
		}

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			TickwatchServer.registerCommands(dispatcher)
		}

		Tickwatch.LOGGER.info("{} initialised (Fabric, server-side)", Tickwatch.MOD_NAME)
	}
}
