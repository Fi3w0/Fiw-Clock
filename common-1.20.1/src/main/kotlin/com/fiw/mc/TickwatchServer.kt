package com.fiw.mc

import com.fiw.StatsManager
import com.fiw.Text
import com.fiw.Tickwatch
import com.fiw.TickwatchCommands
import com.fiw.api.TickwatchApi
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.storage.LevelResource

/**
 * Minecraft 1.20.1 glue shared by the Fabric and Forge modules: each loader forwards
 * its own server events here, and this turns them into plain calls on the Minecraft-free
 * core. Also builds the `/tickwatch` command tree.
 */
object TickwatchServer {

	fun onServerStarting(server: MinecraftServer) {
		val dir = server.getWorldPath(LevelResource.ROOT).resolve(Tickwatch.MOD_ID)
		StatsManager.start(dir) { message -> broadcast(server, message) }
	}

	fun onServerStopping() {
		StatsManager.stop()
	}

	fun onServerTick(server: MinecraftServer) {
		for (player in server.playerList.players) {
			StatsManager.tickPlayer(player.uuid, player.gameProfile.name, player.yRot, player.xRot)
		}
		StatsManager.afterServerTick()
	}

	fun onPlayerJoin(player: ServerPlayer) {
		StatsManager.onPlayerJoin(player.uuid, player.gameProfile.name)
	}

	fun onPlayerQuit(player: ServerPlayer) {
		StatsManager.onPlayerQuit(player.uuid, player.gameProfile.name)
	}

	fun onLivingDeath(victim: LivingEntity, source: DamageSource) {
		if (victim is ServerPlayer) {
			StatsManager.recordDeath(victim.uuid, victim.gameProfile.name)
		}
		val attacker = source.entity
		if (attacker is ServerPlayer) {
			StatsManager.recordKill(attacker.uuid, attacker.gameProfile.name, victim.uuid, victim is ServerPlayer)
		}
	}

	fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
		dispatcher.register(
			Commands.literal(TickwatchCommands.ROOT)
				.executes { context ->
					val player = context.source.player
					if (player != null && allowed(context.source, TickwatchCommands.PERMISSION_STATS)) {
						reply(context.source, TickwatchCommands.statsOf(player.uuid))
					} else {
						reply(context.source, TickwatchCommands.help())
					}
				}
				.then(
					Commands.literal("stats")
						.requires { allowed(it, TickwatchCommands.PERMISSION_STATS) }
						.executes { context ->
							val player = context.source.player
							if (player != null) {
								reply(context.source, TickwatchCommands.statsOf(player.uuid))
							} else {
								reply(context.source, TickwatchCommands.help())
							}
						}
						.then(
							Commands.argument("player", StringArgumentType.word())
								.requires { allowed(it, TickwatchCommands.PERMISSION_STATS_OTHERS) }
								.suggests { _, builder -> SharedSuggestionProvider.suggest(TickwatchCommands.knownNames(), builder) }
								.executes { context ->
									reply(context.source, TickwatchCommands.statsOf(StringArgumentType.getString(context, "player")))
								},
						),
				)
				.then(
					Commands.literal("top")
						.requires { allowed(it, TickwatchCommands.PERMISSION_TOP) }
						.then(
							Commands.argument("stat", StringArgumentType.word())
								.suggests { _, builder -> SharedSuggestionProvider.suggest(TickwatchCommands.rankableStatIds(), builder) }
								.executes { context ->
									val stat = StringArgumentType.getString(context, "stat")
									reply(context.source, TickwatchCommands.top(stat, TickwatchCommands.DEFAULT_TOP))
								}
								.then(
									Commands.argument("count", IntegerArgumentType.integer(1, TickwatchCommands.MAX_TOP))
										.executes { context ->
											val stat = StringArgumentType.getString(context, "stat")
											val count = IntegerArgumentType.getInteger(context, "count")
											reply(context.source, TickwatchCommands.top(stat, count))
										},
								),
						),
				)
				.then(
					Commands.literal("reload")
						.requires { allowed(it, TickwatchCommands.PERMISSION_RELOAD) }
						.executes { context -> reply(context.source, TickwatchCommands.reload()) },
				),
		)
	}

	/** LuckPerms decides when it has an opinion on [node]; otherwise the vanilla permission level does. */
	private fun allowed(source: CommandSourceStack, node: String): Boolean {
		val player = source.player
		if (player != null) {
			TickwatchApi.checkLuckPermsPermission(player.uuid, node)?.let { return it }
		}
		val level = TickwatchCommands.defaultLevel(node)
		return level <= 0 || hasLevel(source, level)
	}

	private fun hasLevel(source: CommandSourceStack, level: Int): Boolean = source.hasPermission(level)

	private fun reply(source: CommandSourceStack, reply: TickwatchCommands.Reply): Int {
		if (!reply.success) {
			for (line in reply.lines) source.sendFailure(component(line))
			return 0
		}
		for (line in reply.lines) source.sendSuccess({ component(line) }, false)
		return 1
	}

	private fun broadcast(server: MinecraftServer, message: String) {
		val component = component(message)
		for (player in server.playerList.players) {
			player.sendSystemMessage(component)
		}
	}

	/** Turns `&`-formatted text (see [Text]) into a chat component. */
	private fun component(text: String): MutableComponent {
		val root = Component.empty()
		for (part in Text.parse(text)) {
			val formats = part.formats.map { ChatFormatting.valueOf(it.name) }.toTypedArray()
			root.append(Component.literal(part.text).withStyle(*formats))
		}
		return root
	}
}
