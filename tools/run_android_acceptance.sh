#!/usr/bin/env bash
# Keep the Gradle exit status, but collect real emulator screenshots regardless.
set +e
gradle -p android --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p runtime-screens/acceptance
echo "Collecting actual APK screenshots and UI diagnostics..."
adb pull /sdcard/Download/GruzSherQA/. runtime-screens/acceptance/ || true
exit "$result"
