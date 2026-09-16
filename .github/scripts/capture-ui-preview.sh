#!/usr/bin/env bash
set -euo pipefail

preview_dir="/sdcard/Download/bema-ui-preview"
adb shell "rm -rf '$preview_dir'"
adb shell wm size 1080x2400
adb shell wm density 420
./gradlew :app:androidApp:connectedDebugAndroidTest -PskipIosTargets
mkdir -p build/ui-preview
adb pull "$preview_dir/." build/ui-preview/
