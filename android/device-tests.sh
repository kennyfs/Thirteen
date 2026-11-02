#!/bin/bash
# Runs the instrumented tests on a local emulator: by default ScreenshotTest, whose screenshots and log land in
# build/screenshots; with ALL=1 every device test. Starts a headless Android 14 emulator (AVD "thirteen", made on the
# first run, with a display as large as the largest size ScreenshotTest tries) and stops it after, unless KEEP=1.
# Needs the emulator and system-images;android-34;google_apis;x86_64 in the SDK, and /dev/kvm. Paths as in
# build-apk.sh, plus ANDROID_AVD_HOME (default ~/.android/avd); the log is device-tests.log.
set -euo pipefail
cd "$(dirname "$0")"
if [ -f local.env ]; then set -a; . ./local.env; set +a; fi
export ANDROID_HOME=${ANDROID_HOME:-~/Android/Sdk}
export ANDROID_AVD_HOME=${ANDROID_AVD_HOME:-~/.android/avd}
GRADLE=${GRADLE:-gradle}
ADB=$ANDROID_HOME/platform-tools/adb
# Only ever the emulator: ScreenshotTest changes the display and font settings of the device it runs on.
export ANDROID_SERIAL=emulator-5554
IMAGE="system-images;android-34;google_apis;x86_64"
exec > >(tee device-tests.log) 2>&1

if [ ! -d "$ANDROID_AVD_HOME/thirteen.avd" ]; then
  mkdir -p "$ANDROID_AVD_HOME"
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n thirteen -k "$IMAGE" -d pixel_6
fi
started=0
if ! "$ADB" get-state > /dev/null 2>&1; then
  "$ANDROID_HOME/emulator/emulator" -avd thirteen -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -skin 1600x2600 > emulator.log 2>&1 &
  started=1
fi
stop() {
  if [ "$started" = 1 ] && [ "${KEEP:-0}" != 1 ]; then "$ADB" emu kill > /dev/null 2>&1 || true; fi
}
trap stop EXIT
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
for s in window_animation_scale transition_animation_scale animator_duration_scale; do "$ADB" shell settings put global $s 0; done

status=0
if [ "${ALL:-0}" = 1 ]; then
  "$GRADLE" --no-daemon connectedDebugAndroidTest || status=$?
else
  "$ADB" shell rm -rf /data/local/tmp/shots
  "$GRADLE" --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=io.github.kennyfs.thirteen.ScreenshotTest || status=$?
  rm -rf build/screenshots
  mkdir -p build
  "$ADB" pull /data/local/tmp/shots build/screenshots
  "$ADB" logcat -d -s ScreenshotTest TestRunner > build/screenshots/log.txt
  echo "Screenshots: $PWD/build/screenshots"
fi
exit $status
