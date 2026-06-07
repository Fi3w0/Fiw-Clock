allprojects {
	group = providers.gradleProperty("maven_group").get()
	version = providers.gradleProperty("mod_version").get()

	repositories {
		mavenCentral()
		maven("https://maven.neoforged.net/releases")
		maven("https://thedarkcolour.github.io/KotlinForForge/")
		maven("https://maven.fabricmc.net/")
	}
}
