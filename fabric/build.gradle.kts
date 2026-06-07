import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	kotlin("jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-fabric")
}

evaluationDependsOn(":common")

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	mappings(loom.officialMojangMappings())

	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("fabric_loader_version").get()}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// Shared, Minecraft-free logic. Compiled against here and bundled into the jar below.
	implementation(project(":common"))
}

tasks.processResources {
	inputs.property("version", project.version)
	filesMatching("fabric.mod.json") {
		expand("version" to project.version)
	}
}

// Bundle the common module's classes + resources (the icon) into the Fabric jar.
// They carry no Minecraft references, so Loom's remap step leaves them untouched.
val commonMain = project(":common").extensions.getByType(SourceSetContainer::class.java).getByName("main")
tasks.named<Jar>("jar") {
	from(commonMain.output)
}

java {
	withSourcesJar()
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
