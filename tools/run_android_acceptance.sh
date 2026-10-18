#!/usr/bin/env bash
# The emulator runner executes each 'script:' line in a separate shell.
# Keep Gradle's exit code while always attempting to collect real screenshots.
set +e
gradle -p android --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p runtime-screens
adb pull /sdcard/Android/data/ru.kbr.cargoshare.referencepreview/files/acceptance runtime-screens/ || true
exit "$result"
