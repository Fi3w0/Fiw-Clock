import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	// Declared once here so all modules share one plugin classloader (Loom must be able to
	// see the Kotlin plugin). Each module applies only what it needs.
	id("org.jetbrains.kotlin.jvm") apply false
	id("net.fabricmc.fabric-loom-remap") apply false
	id("net.neoforged.moddev") apply false
	id("net.neoforged.moddev.legacyforge") apply false
}

allprojects {
	group = providers.gradleProperty("maven_group").get()
	version = providers.gradleProperty("mod_version").get()

	repositories {
		mavenCentral()
		maven("https://maven.neoforged.net/releases")
		maven("https://maven.minecraftforge.net/")
		maven("https://thedarkcolour.github.io/KotlinForForge/")
		maven("https://maven.fabricmc.net/")
	}
}

subprojects {
	// The jars run on whatever Kotlin the loader's language mod ships, so only use stdlib
	// API that exists there (see kotlin_api_version).
	plugins.withId("org.jetbrains.kotlin.jvm") {
		extensions.configure<KotlinJvmProjectExtension> {
			compilerOptions {
				val kotlinApi = KotlinVersion.fromVersion(providers.gradleProperty("kotlin_api_version").get())
				apiVersion.set(kotlinApi)
				languageVersion.set(kotlinApi)
			}
		}
	}
}
