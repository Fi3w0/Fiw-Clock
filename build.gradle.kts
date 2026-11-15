import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
	// Declared once here so all modules share one plugin classloader (Loom must be able to
	// see the Kotlin plugin). Each module applies only what it needs.
	id("org.jetbrains.kotlin.jvm") apply false
	id("net.fabricmc.fabric-loom-remap") apply false
	id("net.fabricmc.fabric-loom") apply false
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

tasks.register<Exec>("smokeTest") {
	val module = providers.gradleProperty("smokeTarget").getOrElse("fabric-26.1.2")
	dependsOn("$module:build")
	val artifactModule = module
	commandLine("python3", ".github/scripts/smoke-test.py", module,
		"$module/build/libs/fiw-clock-$artifactModule-${project.version}.jar")
	providers.gradleProperty("smokeSuiteFile").orNull?.let { args("--suite-file", it) }
	providers.gradleProperty("smokeExtraJars").orNull?.split("|")?.forEach { args("--extra-jar", it) }
	providers.gradleProperty("smokeCommands").orNull?.split("|")?.forEach { args("--command", it) }
	providers.gradleProperty("smokeExpected").orNull?.split("|")?.forEach { args("--expect", it) }
}

tasks.register<Exec>("installPlayerTestTools") {
    inputs.files(".github/player-tests/package.json", ".github/player-tests/package-lock.json")
    outputs.dir(".github/player-tests/node_modules")
	commandLine("npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund",
		"--cache", "build/npm-cache", "--prefix", ".github/player-tests")
}


tasks.register<Exec>("integrationTest") {
    val module = providers.gradleProperty("smokeTarget").getOrElse("fabric-26.1.2")
    if (!providers.gradleProperty("useBuiltJars").getOrElse("false").toBoolean()) {
        dependsOn("$module:build", "installPlayerTestTools")
    }
    commandLine("python3", ".github/scripts/verify-workspace.py", module)
}


tasks.register<Exec>("playerTest") {
    val module = providers.gradleProperty("smokeTarget").getOrElse("fabric-26.1.2")
    dependsOn("$module:build", "installPlayerTestTools")
    val artifactModule = module
    commandLine("python3", ".github/scripts/smoke-test.py", module,
        "$module/build/libs/fiw-clock-$artifactModule-${project.version}.jar",
        "--player-script", ".github/player-tests/gameplay.cjs", "--player-phases", "first,restart")
}
