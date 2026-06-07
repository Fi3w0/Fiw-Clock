package com.fiw

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Crash-safe JSON persistence for player stats.
 *
 * Durability strategy (survives sudden power loss / kill -9):
 *  1. Serialize to a sibling `.tmp` file and `fsync` it.
 *  2. Rotate the current file to `.bak` (previous good copy is always kept).
 *  3. Atomically rename `.tmp` over the real file.
 *
 * A torn write can therefore only ever damage the `.tmp` file; the real file and its
 * backup are always complete. [load] falls back to the backup if the main file is
 * unreadable.
 *
 * All disk writes run on a single dedicated daemon thread so the server thread never
 * blocks on I/O. Writes are serialized, so they can never interleave or corrupt output.
 */
class StatsStorage(private val file: Path) {

	private val backup: Path = sibling(".bak")
	private val tmp: Path = sibling(".tmp")
	private val gson = GsonBuilder().setPrettyPrinting().create()

	private val io = Executors.newSingleThreadExecutor { runnable ->
		Thread(runnable, "${Tickwatch.MOD_NAME}-IO").apply { isDaemon = true }
	}

	private fun sibling(suffix: String): Path =
		file.resolveSibling(file.fileName.toString() + suffix)

	// ---- loading -------------------------------------------------------------

	fun load(): Map<UUID, PlayerStats> {
		readFrom(file)?.let { return it }
		readFrom(backup)?.let {
			Tickwatch.LOGGER.warn("Main stats file was unreadable; recovered {} entries from backup", it.size)
			return it
		}
		return emptyMap()
	}

	private fun readFrom(path: Path): Map<UUID, PlayerStats>? {
		if (!Files.exists(path)) return null
		return try {
			val text = Files.readString(path)
			val root = JsonParser.parseString(text).asJsonObject
			val players = root.getAsJsonObject("players") ?: return HashMap()
			val result = HashMap<UUID, PlayerStats>(players.size())
			for ((key, element) in players.entrySet()) {
				try {
					val obj = element.asJsonObject
					result[UUID.fromString(key)] = PlayerStats(
						name = obj.get("name")?.asString ?: "unknown",
						playTimeTicks = obj.get("playTimeTicks")?.asLong ?: 0L,
						kills = obj.get("kills")?.asLong ?: 0L,
						deaths = obj.get("deaths")?.asLong ?: 0L,
					)
				} catch (e: Exception) {
					// Skip a single malformed entry rather than losing the whole file.
					Tickwatch.LOGGER.warn("Skipping malformed stats entry '{}': {}", key, e.toString())
				}
			}
			result
		} catch (e: Exception) {
			Tickwatch.LOGGER.error("Failed to read stats from {}: {}", path.fileName, e.toString())
			null
		}
	}

	// ---- saving --------------------------------------------------------------

	fun saveAsync(data: Map<UUID, PlayerStats>) {
		try {
			io.submit { writeAtomic(data) }
		} catch (e: RejectedExecutionException) {
			// Executor is shutting down; the blocking save on stop covers us.
		}
	}

	fun saveBlocking(data: Map<UUID, PlayerStats>) {
		writeAtomic(data)
	}

	@Synchronized
	private fun writeAtomic(data: Map<UUID, PlayerStats>) {
		try {
			Files.createDirectories(file.parent)

			val bytes = serialize(data).toByteArray(Charsets.UTF_8)

			// 1. Write + fsync the temp file.
			FileChannel.open(tmp, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
				val buffer = ByteBuffer.wrap(bytes)
				while (buffer.hasRemaining()) channel.write(buffer)
				channel.force(true)
			}

			// 2. Keep the last good copy as a backup.
			if (Files.exists(file)) {
				try {
					Files.move(file, backup, REPLACE_EXISTING)
				} catch (e: Exception) {
					Tickwatch.LOGGER.warn("Could not rotate stats backup: {}", e.toString())
				}
			}

			// 3. Atomically swap the temp file into place.
			try {
				Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
			} catch (e: AtomicMoveNotSupportedException) {
				Files.move(tmp, file, REPLACE_EXISTING)
			}
		} catch (e: Exception) {
			Tickwatch.LOGGER.error("Failed to write stats: {}", e.toString())
		}
	}

	private fun serialize(data: Map<UUID, PlayerStats>): String {
		val root = JsonObject()
		val players = JsonObject()
		for ((id, s) in data) {
			val obj = JsonObject()
			obj.addProperty("name", s.name)
			obj.addProperty("playTimeTicks", s.playTimeTicks)
			obj.addProperty("playTimeFormatted", formatTicks(s.playTimeTicks))
			obj.addProperty("kills", s.kills)
			obj.addProperty("deaths", s.deaths)
			players.add(id.toString(), obj)
		}
		root.add("players", players)
		return gson.toJson(root)
	}

	fun shutdown() {
		io.shutdown()
		try {
			if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
				io.shutdownNow()
			}
		} catch (e: InterruptedException) {
			io.shutdownNow()
			Thread.currentThread().interrupt()
		}
	}

	private companion object {
		/** Human-readable companion to playTimeTicks; informational only, ignored on load. */
		fun formatTicks(ticks: Long): String {
			val totalSeconds = ticks / 20
			val hours = totalSeconds / 3600
			val minutes = (totalSeconds % 3600) / 60
			val seconds = totalSeconds % 60
			return "%dh %02dm %02ds".format(hours, minutes, seconds)
		}
	}
}