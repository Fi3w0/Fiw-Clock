import java.util.Properties
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	kotlin("jvm")
}

// Minecraft, loader and Java versions of this target (see this module's gradle.properties).
val targetProperties = Properties().apply { file("gradle.properties").inputStream().use { load(it) } }
fun target(name: String): String = targetProperties.getProperty(name)

val minecraftVersion = target("minecraft_version")
val javaVersion = target("java_version")

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-fabric-$minecraftVersion")
}

evaluationDependsOn(":core")

// Minecraft code shared with the other loader of this Minecraft version.
kotlin.sourceSets.getByName("main").kotlin.srcDir(rootProject.file("common-$minecraftVersion/src/main/kotlin"))

dependencies {
	minecraft("com.mojang:minecraft:$minecraftVersion")
	mappings(loom.officialMojangMappings())

	modImplementation("net.fabricmc:fabric-loader:${target("fabric_loader_version")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${target("fabric_api_version")}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${target("fabric_kotlin_version")}")

	// Shared, Minecraft-free logic. Compiled against here and bundled into the jar below.
	implementation(project(":core"))
}

tasks.processResources {
	val properties = mapOf(
		"version" to project.version,
		"mod_id" to providers.gradleProperty("mod_id").get(),
		"minecraft_dependency" to target("minecraft_dependency"),
		"fabric_loader_min" to target("fabric_loader_min"),
		"java_version" to javaVersion,
	)
	inputs.properties(properties)
	filesMatching("fabric.mod.json") {
		expand(properties)
	}
}

// Bundle the core module's classes + resources (the icon) into the Fabric jar.
// They carry no Minecraft references, so Loom's remap step leaves them untouched.
val coreMain = project(":core").extensions.getByType(SourceSetContainer::class.java).getByName("main")
tasks.named<Jar>("jar") {
	from(coreMain.output)
}

java {
	withSourcesJar()
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
