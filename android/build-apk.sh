#!/bin/bash
# Builds the release APK and the bundle for Google Play, and copies them to $THIRTEEN_APK_DIR (default build/apk) as
# thirteen-<versionName>.apk and .aab.
# It is signed with the key that $THIRTEEN_SIGNING (default ~/.android/thirteen-release.txt) describes, in lines
# "keystore PATH", "alias NAME" and "password PASS"; without that file, with the debug key. JAVA_HOME (JDK 17),
# ANDROID_HOME (default ~/Android/Sdk), GRADLE (default gradle) and GRADLE_USER_HOME come from the environment or
# from local.env next to this script (not in the repository). The log is build-apk.log.
set -euo pipefail
cd "$(dirname "$0")"
if [ -f local.env ]; then set -a; . ./local.env; set +a; fi
export ANDROID_HOME=${ANDROID_HOME:-~/Android/Sdk}
GRADLE=${GRADLE:-gradle}
NOTES=${THIRTEEN_SIGNING:-~/.android/thirteen-release.txt}
if [ -f "$NOTES" ]; then
  export THIRTEEN_KEYSTORE=$(awk '$1 == "keystore" { print $2 }' "$NOTES")
  export THIRTEEN_KEY_ALIAS=$(awk '$1 == "alias" { print $2 }' "$NOTES")
  export THIRTEEN_KEYSTORE_PASSWORD=$(awk '$1 == "password" { print $2 }' "$NOTES")
  export THIRTEEN_KEY_PASSWORD=$THIRTEEN_KEYSTORE_PASSWORD
fi
"$GRADLE" --no-daemon testDebugUnitTest assembleRelease bundleRelease 2>&1 | tee build-apk.log
name=$(sed -n 's/.*"versionName": *"\([^"]*\)".*/\1/p' app/build/outputs/apk/release/output-metadata.json)
dir=${THIRTEEN_APK_DIR:-build/apk}
mkdir -p "$dir"
cp app/build/outputs/apk/release/app-release.apk "$dir/thirteen-$name.apk"
cp app/build/outputs/bundle/release/app-release.aab "$dir/thirteen-$name.aab"
echo "$dir/thirteen-$name.apk"
echo "$dir/thirteen-$name.aab"
