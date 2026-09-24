# Every jar Tickwatch releases, newest Minecraft first: "<module> <modrinth loader> <minecraft version>".
# Sourced by collect-jars.sh and publish-modrinth.sh.
TARGETS=(
  "fabric-1.21.11 fabric 1.21.11"
  "neoforge-1.21.11 neoforge 1.21.11"
  "fabric-1.21.1 fabric 1.21.1"
  "neoforge-1.21.1 neoforge 1.21.1"
  "fabric-1.20.1 fabric 1.20.1"
  "forge-1.20.1 forge 1.20.1"
)

# Release jar of <module> for mod version <version> (each module's archivesName is fiw-clock-<module>).
jar_path() {
  echo "$1/build/libs/fiw-clock-$1-$2.jar"
}
