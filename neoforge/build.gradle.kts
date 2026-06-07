import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.neoforged.moddev")
	kotlin("jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-neoforge")
}

evaluationDependsOn(":common")

neoForge {
	version = providers.gradleProperty("neoforge_version").get()

	mods {
		register(providers.gradleProperty("mod_id").get()) {
			sourceSet(sourceSets.getByName("main"))
		}
	}
}

dependencies {
	// Kotlin language provider for NeoForge. Must also be installed on the server.
	implementation("thedarkcolour:kotlinforforge-neoforge:${providers.gradleProperty("kotlin_for_forge_version").get()}")

	// Shared, Minecraft-free logic. Compiled against here and bundled into the jar below.
	implementation(project(":common"))
}

tasks.processResources {
	inputs.property("version", project.version)
	filesMatching("META-INF/neoforge.mods.toml") {
		expand("version" to project.version)
	}
}

// Bundle the common module's classes + resources (the icon) into the NeoForge jar.
val commonMain = project(":common").extensions.getByType(SourceSetContainer::class.java).getByName("main")
tasks.named<Jar>("jar") {
	from(commonMain.output)
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(21)
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_21)
	}
}
