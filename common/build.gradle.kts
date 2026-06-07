import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm")
}

base {
	archivesName.set("${providers.gradleProperty("archives_base_name").get()}-common")
}

dependencies {
	// Both are shipped by Minecraft / the loader at runtime, so we only compile against them.
	// Keeping them out of the jar is what lets `common` stay completely Minecraft-free.
	compileOnly("com.google.code.gson:gson:2.11.0")
	compileOnly("org.slf4j:slf4j-api:2.0.16")
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
