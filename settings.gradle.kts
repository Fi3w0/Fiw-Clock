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
		id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlin_version")
	}
}

include("common")
include("fabric")
include("neoforge")

rootProject.name = "fiw-clock"
