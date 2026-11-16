#!/usr/bin/env bash
# Build HunTracK for the iOS Simulator (no distribution signing) and capture
# App Store screenshots with `xcrun simctl io <udid> screenshot`.
# A UI test drives the real app. This script does not upload to App Store Connect.
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
export ROOT

PLAN_JSON="${RUNNER_TEMP:-/tmp}/screenshot-plan.json"
APP_DD="${RUNNER_TEMP:-/tmp}/hunttrack-app-dd"
TEST_DD="${RUNNER_TEMP:-/tmp}/hunttrack-test-dd"
SHOTS="$ROOT/screenshots"
FLAT="$ROOT/.github/scripts/flatten_png.py"
mkdir -p "$SHOTS"
rm -rf "$SHOTS"/*
: > "$SHOTS/MANIFEST.txt"

select_xcode() {
  shopt -s nullglob
  local apps=(/Applications/Xcode_26*.app /Applications/Xcode_16*.app)
  shopt -u nullglob
  if [ "${#apps[@]}" -gt 0 ]; then
    local chosen
    chosen="$(printf '%s\n' "${apps[@]}" | sort -V | tail -n 1)"
    echo "Selecting $chosen"
    sudo xcode-select -s "$chosen/Contents/Developer"
  fi
  xcodebuild -version
}

build_app() {
  echo "Building HunTracK for the simulator"
  rm -rf "$APP_DD"
  xcodebuild \
    -project ios/HunTracK/HunTracK.xcodeproj \
    -scheme HunTracK \
    -configuration Debug \
    -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$APP_DD" \
    ARCHS=arm64 \
    ONLY_ACTIVE_ARCH=YES \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY="" \
    COMPILER_INDEX_STORE_ENABLE=NO \
    ENABLE_PREVIEWS=NO \
    build
  APP="$(find "$APP_DD/Build/Products" -path '*iphonesimulator*' -name 'HunTracK.app' -type d -print -quit)"
  [ -n "$APP" ] || { echo "HunTracK.app was not produced"; exit 1; }
  SHORT="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Info.plist")"
  BUILD="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$APP/Info.plist")"
  echo "Built $SHORT ($BUILD) at $APP"
  if [ "$SHORT" != "1.1.31" ] || [ "$BUILD" != "44" ]; then
    echo "::error::Built $SHORT ($BUILD); expected 1.1.31 (44)"
    exit 1
  fi
  export APP SHORT BUILD
}

build_tests() {
  local udid="$1"
  echo "Building the screenshot UI test for $udid"
  rm -rf "$TEST_DD"
  xcodebuild build-for-testing \
    -project ios/screenshots/HunTracKScreenshots.xcodeproj \
    -scheme HunTracKScreenshots \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath "$TEST_DD" \
    ARCHS=arm64 \
    ONLY_ACTIVE_ARCH=YES \
    CODE_SIGN_IDENTITY="-" \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_STYLE=Manual \
    DEVELOPMENT_TEAM="" \
    COMPILER_INDEX_STORE_ENABLE=NO
  XCTESTRUN="$(find "$TEST_DD" -name '*.xctestrun' -print -quit)"
  [ -n "$XCTESTRUN" ] || { echo "No xctestrun was produced"; exit 1; }
  export XCTESTRUN
  echo "Test runner: $XCTESTRUN"
}

set_location() {
  local udid="$1"
  # Balmoral, Aberdeenshire. On land, so the outdoor map is a field rather than open water.
  if ! xcrun simctl location "$udid" set "57.0402,-3.2298"; then
    xcrun simctl location "$udid" set -- 57.0402 -3.2298 || true
  fi
}

status_bar() {
  local udid="$1"
  xcrun simctl status_bar "$udid" override \
    --time "9:41" \
    --batteryState charged \
    --batteryLevel 100 \
    --cellularBars 4 \
    --wifiBars 3 \
    || echo "status_bar override failed for $udid"
}

boot_simulator() {
  local udid="$1"
  local state
  state="$(xcrun simctl list devices | awk -v id="$udid" 'index($0, id) {print}')"
  if [[ "$state" != *Booted* ]]; then
    xcrun simctl shutdown all >/dev/null 2>&1 || true
    xcrun simctl boot "$udid" || true
    xcrun simctl bootstatus "$udid" -b
    open -a Simulator --args -CurrentDeviceUDID "$udid" || open -a Simulator
    sleep 2
  fi
  status_bar "$udid"
}

png_size() {
  python3 "$FLAT" --size "$1"
}

ensure_size() {
  local udid="$1" expect_w="$2" expect_h="$3"
  local probe="${RUNNER_TEMP:-/tmp}/probe-${udid}.png"
  local attempt w h
  for attempt in 1 2 3 4; do
    rm -f "$probe"
    if ! xcrun simctl io "$udid" screenshot "$probe"; then
      sleep 2
      continue
    fi
    if ! read -r w h < <(png_size "$probe"); then
      sleep 2
      continue
    fi
    echo "Probe $udid is ${w}x${h} (want ${expect_w}x${expect_h})"
    if [ "$w" = "$expect_w" ] && [ "$h" = "$expect_h" ]; then
      return 0
    fi
    if [ "$w" = "$expect_h" ] && [ "$h" = "$expect_w" ]; then
      echo "Simulator is landscape; rotating towards portrait"
      osascript -e 'tell application "Simulator" to activate' || true
      osascript -e 'tell application "System Events" to key code 123 using command down' || true
      sleep 2
      continue
    fi
    return 1
  done
  return 1
}

watch_shots() {
  local out="$1"
  set +e
  mkdir -p "$out"
  local root="$HOME/Library/Developer/CoreSimulator/Devices"
  while [ ! -f "$out/.test-finished" ]; do
    local req
    req="$(find "$root" -maxdepth 10 -name 'huntrack-shot-request-*' -type f 2>/dev/null | head -n 1)"
    if [ -z "$req" ]; then
      sleep 0.3
      continue
    fi
    local base name dir udid dest
    base="$(basename "$req")"
    name="${base#huntrack-shot-request-}"
    dir="$(dirname "$req")"
    udid="$(printf '%s\n' "$req" | sed -n 's|.*/Devices/\([A-Fa-f0-9-]*\)/data/.*|\1|p')"
    mv "$req" "$req.working" 2>/dev/null || continue
    dest="$out/$name.png"
    if [ -n "$udid" ]; then
      xcrun simctl status_bar "$udid" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3 >/dev/null 2>&1
      sleep 0.5
      xcrun simctl io "$udid" screenshot "$dest" || sleep 1
      if [ ! -s "$dest" ]; then
        xcrun simctl io "$udid" screenshot "$dest" || true
      fi
      echo "Captured $name from $udid"
    else
      echo "Could not read a simulator id from $req"
    fi
    printf 'done\n' > "$dir/huntrack-shot-done-$name"
    rm -f "$req.working"
  done
}

refresh_location() {
  local flag="$1"
  set +e
  while [ ! -f "$flag" ]; do
    local id
    while IFS= read -r id; do
      [ -n "$id" ] || continue
      xcrun simctl location "$id" set "57.0402,-3.2298" >/dev/null 2>&1 || true
    done < <(xcrun simctl list devices | sed -n 's/.*(\([A-Fa-f0-9-]\{36\}\)) (Booted).*/\1/p')
    sleep 2
  done
}

stop_pid() {
  local pid="$1"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
}

caption_for() {
  case "$1" in
    01-home) echo "Home dashboard: hunting conditions, live weather, sun and moon" ;;
    02-map) echo "Outdoor map at the simulated Balmoral GPS fix" ;;
    03-weather) echo "Weather forecast for that position" ;;
    04-species) echo "UK species list and legal seasons" ;;
    05-red-deer) echo "Red deer identification and open seasons" ;;
    06-wind) echo "Wind direction and downwind scent plume" ;;
    *) echo "App screen" ;;
  esac
}

flatten_and_record() {
  local folder="$1" device_name="$2" udid="$3" expect_w="$4" expect_h="$5"
  local dir="$SHOTS/$folder"
  local names=(01-home 02-map 03-weather 04-species 05-red-deer 06-wind)
  local name src tmp info
  {
    echo
    echo "[$folder] $device_name ($udid) expected ${expect_w}x${expect_h}"
  } >> "$SHOTS/MANIFEST.txt"
  local ok=0
  for name in "${names[@]}"; do
    src="$dir/$name.png"
    if [ ! -s "$src" ]; then
      echo "MISSING $folder/$name.png" | tee -a "$SHOTS/MANIFEST.txt"
      ok=1
      continue
    fi
    tmp="$src.rgb.png"
    if ! python3 "$FLAT" "$src" "$tmp"; then
      echo "FLATTEN $folder/$name.png" | tee -a "$SHOTS/MANIFEST.txt"
      ok=1
      continue
    fi
    mv "$tmp" "$src"
    info="$(python3 -c 'import sys; sys.path.insert(0, sys.argv[1]); from flatten_png import png_info; print("%dx%d depth=%s color=%s" % png_info(open(sys.argv[2], "rb").read()))' \
      "$ROOT/.github/scripts" "$src")"
    echo "$folder/$name.png $info $(caption_for "$name")" | tee -a "$SHOTS/MANIFEST.txt"
    local w h
    read -r w h < <(png_size "$src")
    if [ "$w" != "$expect_w" ] || [ "$h" != "$expect_h" ]; then
      echo "SIZE $folder/$name.png is ${w}x${h}" | tee -a "$SHOTS/MANIFEST.txt"
      ok=1
    fi
    local color
    color="$(printf '%s\n' "$info" | sed -n 's/.*color=\([0-9]*\).*/\1/p')"
    if [ "$color" != "2" ]; then
      echo "ALPHA $folder/$name.png color type $color" | tee -a "$SHOTS/MANIFEST.txt"
      ok=1
    fi
  done
  return "$ok"
}

run_slot() {
  local udid="$1" folder="$2" device_name="$3" expect_w="$4" expect_h="$5"
  local out="$SHOTS/$folder"
  rm -rf "$out"
  mkdir -p "$out"
  boot_simulator "$udid"
  if ! ensure_size "$udid" "$expect_w" "$expect_h"; then
    echo "::error::$device_name ($udid) did not capture at ${expect_w}x${expect_h}"
    return 1
  fi
  xcrun simctl uninstall "$udid" uk.hunttrack.app >/dev/null 2>&1 || true
  xcrun simctl install "$udid" "$APP"
  xcrun simctl privacy "$udid" grant location uk.hunttrack.app || true
  set_location "$udid"
  find "$HOME/Library/Developer/CoreSimulator/Devices" -maxdepth 10 -name 'huntrack-shot-*' -delete 2>/dev/null || true
  rm -f "$out/.test-finished"

  watch_shots "$out" &
  local watcher=$!
  refresh_location "$out/.test-finished" &
  local refresher=$!

  set +e
  xcodebuild test-without-building \
    -xctestrun "$XCTESTRUN" \
    -destination "platform=iOS Simulator,id=$udid" \
    -only-testing:HunTracKScreenshotsUITests/ScreenshotTour/testCaptureTour \
    -parallel-testing-enabled NO \
    -maximum-concurrent-test-simulator-destinations 1 \
    CODE_SIGN_IDENTITY="-" \
    CODE_SIGNING_REQUIRED=NO
  local status=$?
  set -e
  touch "$out/.test-finished"
  sleep 1
  stop_pid "$watcher"
  stop_pid "$refresher"
  rm -f "$out/.test-finished"
  if [ "$status" -ne 0 ]; then
    echo "::error::UI tour failed on $device_name (exit $status)"
    return "$status"
  fi
  flatten_and_record "$folder" "$device_name" "$udid" "$expect_w" "$expect_h"
}

main() {
  select_xcode
  python3 .github/scripts/ios-screenshot-devices.py > "$PLAN_JSON"
  echo "Device plan:"
  cat "$PLAN_JSON"
  local plan_count
  plan_count="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["plans"]))' "$PLAN_JSON")"
  [ "$plan_count" -ge 1 ]
  # The newest runtime is first. iPhone 17 Pro Max on that runtime is
  # 1320x2868; the tour checks the pixels again before it keeps a device.
  local chosen=0
  local udid
  udid="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][0]["slots"][0]["udid"])' "$PLAN_JSON")"
  build_app
  build_tests "$udid"

  {
    echo "HunTracK iOS $SHORT ($BUILD)"
    echo "bundle uk.hunttrack.app"
    echo "mode local guest (no account). Location grant applied before launch."
    echo "map centre 57.0402,-3.2298 (Balmoral)"
  } >> "$SHOTS/MANIFEST.txt"

  local slots s folder device_w device_h device_udid device_name
  slots="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"]))' "$PLAN_JSON" "$chosen")"
  local failed=0
  for ((s = 0; s < slots; s++)); do
    device_udid="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"][int(sys.argv[3])]["udid"])' "$PLAN_JSON" "$chosen" "$s")"
    device_name="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"][int(sys.argv[3])]["name"])' "$PLAN_JSON" "$chosen" "$s")"
    folder="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"][int(sys.argv[3])]["folder"])' "$PLAN_JSON" "$chosen" "$s")"
    device_w="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"][int(sys.argv[3])]["width"])' "$PLAN_JSON" "$chosen" "$s")"
    device_h="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plans"][int(sys.argv[2])]["slots"][int(sys.argv[3])]["height"])' "$PLAN_JSON" "$chosen" "$s")"
    echo "==== $folder on $device_name ===="
    if ! run_slot "$device_udid" "$folder" "$device_name" "$device_w" "$device_h"; then
      failed=1
      break
    fi
  done
  xcrun simctl shutdown all >/dev/null 2>&1 || true
  echo "======== MANIFEST ========"
  cat "$SHOTS/MANIFEST.txt"
  if [ "$failed" -ne 0 ]; then
    exit 1
  fi
}

main "$@"
