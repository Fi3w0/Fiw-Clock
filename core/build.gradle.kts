import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-core")
}

dependencies {
	// Both are shipped by Minecraft / the loader at runtime, so we only compile against them.
	// Keeping them out of the jar is what lets `core` stay completely Minecraft-free.
	compileOnly("com.google.code.gson:gson:2.11.0")
	compileOnly("org.slf4j:slf4j-api:2.0.16")

	// Optional integration: only touched at runtime when LuckPerms is installed.
	// 5.4 is the API shipped by every LuckPerms build Tickwatch supports.
	compileOnly("net.luckperms:api:${providers.gradleProperty("luckperms_api_version").get()}")

	testImplementation(kotlin("test"))
	testImplementation("com.google.code.gson:gson:2.11.0")
	testImplementation("org.slf4j:slf4j-api:2.0.16")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
}

// Java 17 bytecode: the engine is bundled into every loader jar, and Minecraft 1.20.1
// still runs on Java 17. Newer targets load Java 17 classes just fine.
java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(17)
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}
