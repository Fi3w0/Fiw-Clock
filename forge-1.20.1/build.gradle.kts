import java.util.Properties
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.neoforged.moddev.legacyforge")
	kotlin("jvm")
}

// Minecraft, loader and Java versions of this target (see this module's gradle.properties).
val targetProperties = Properties().apply { file("gradle.properties").inputStream().use { load(it) } }
fun target(name: String): String = targetProperties.getProperty(name)

val minecraftVersion = target("minecraft_version")
val javaVersion = target("java_version")
val forgeModId = providers.gradleProperty("forge_mod_id").get()

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-forge-$minecraftVersion")
}

evaluationDependsOn(":core")
val coreMain = project(":core").extensions.getByType(SourceSetContainer::class.java).getByName("main")

// Minecraft code shared with the other loader of this Minecraft version.
kotlin.sourceSets.getByName("main").kotlin.srcDir(rootProject.file("common-$minecraftVersion/src/main/kotlin"))

// Computed outside the block: inside legacyForge {}, `minecraftVersion` is the extension's own property.
val forgeVersion = "$minecraftVersion-${target("forge_version")}"

legacyForge {
	version = forgeVersion

	mods {
		register(forgeModId) {
			sourceSet(sourceSets.getByName("main"))
			sourceSet(coreMain)
		}
	}
}

dependencies {
	// Kotlin language provider for Forge. Must also be installed on the server.
	implementation("thedarkcolour:kotlinforforge:${target("kotlin_for_forge_version")}")

	// Shared, Minecraft-free logic. Compiled against here and bundled into the jar below.
	implementation(project(":core"))
}

tasks.processResources {
	val properties = mapOf(
		"version" to project.version,
		"forge_mod_id" to forgeModId,
		"minecraft_version_range" to target("minecraft_version_range"),
		"forge_version_range" to target("forge_version_range"),
		"kotlin_for_forge_version_range" to target("kotlin_for_forge_version_range"),
	)
	inputs.properties(properties)
	filesMatching("META-INF/mods.toml") {
		expand(properties)
	}
}

// Bundle the core module's classes + resources (the icon) into the Forge jar. The jar is
// then reobfuscated to SRG names by ModDevGradle; core has no Minecraft references.
tasks.named<Jar>("jar") {
	from(coreMain.output)
}

java {
	sourceCompatibility = JavaVersion.toVersion(javaVersion)
	targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(javaVersion.toInt())
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.fromTarget(javaVersion))
	}
}
