MODRINTH_PROJECT="knjOyXW2"
RELEASE_TYPE="beta"
DISPLAY_NAME="Tickwatch"
KOTLIN_RUNTIME="external"
OPTIONAL_LUCKPERMS="true"
TARGETS=(
  "fabric-26.1.2 fabric 26.1.2"
  "fabric-1.21.11 fabric 1.21.11"
  "neoforge-1.21.11 neoforge 1.21.11"
  "fabric-1.21.8 fabric 1.21.8"
  "neoforge-1.21.8 neoforge 1.21.8"
  "fabric-1.21.1 fabric 1.21.1"
  "neoforge-1.21.1 neoforge 1.21.1"
  "fabric-1.20.1 fabric 1.20.1"
  "forge-1.20.1 forge 1.20.1"
)

jar_path() {
  local module="$1"
  echo "$1/build/libs/fiw-clock-$module-$2.jar"
}
