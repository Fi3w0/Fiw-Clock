# Release configuration, sourced by collect-jars.sh, publish-modrinth.sh and the workflows.

# Modrinth project id of Tickwatch (https://modrinth.com/mod/fiw-clock). Publishing refuses to
# run while this is empty. Note: the slug "tickwatch" belongs to an unrelated project.
MODRINTH_PROJECT="knjOyXW2"

# Every jar Tickwatch releases, newest Minecraft first: "<module> <modrinth loader> <minecraft version>".
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
