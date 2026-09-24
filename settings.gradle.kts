pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		maven("https://maven.neoforged.net/releases")
		maven("https://maven.fabricmc.net/")
	}

	plugins {
		id("net.fabricmc.fabric-loom-remap") version providers.gradleProperty("loom_version")
		id("net.neoforged.moddev") version providers.gradleProperty("moddev_version")
		// Same artifact as moddev: adds MinecraftForge (1.20.1 and older) support.
		id("net.neoforged.moddev.legacyforge") version providers.gradleProperty("moddev_version")
		id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlin_version")
	}
}

// Minecraft-free engine, bundled into every loader jar.
include("core")

// One module per Minecraft version and loader, newest first. Each one also compiles the
// shared, version-specific Minecraft code in common-<version>/.
include("fabric-1.21.11")
include("neoforge-1.21.11")
include("fabric-1.21.1")
include("neoforge-1.21.1")
include("fabric-1.20.1")
// NeoForge has no 1.20.1 build, so the Forge family is covered by MinecraftForge there.
include("forge-1.20.1")

rootProject.name = "fiw-clock"
