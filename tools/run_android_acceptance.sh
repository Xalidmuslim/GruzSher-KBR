#!/usr/bin/env bash
# Run in a single shell: the emulator action otherwise discards cwd between lines.
# UI test screenshots are written inside the debug app's private files directory.
set +e
gradle -p android --no-daemon connectedDebugAndroidTest
result=$?

pkg=ru.kbr.cargoshare.referencepreview
mkdir -p runtime-screens/acceptance
echo "Collecting captured emulator screenshots and failure diagnostics..."
adb shell run-as "$pkg" ls files/acceptance || true
for file in $(adb shell run-as "$pkg" ls files/acceptance 2>/dev/null | tr -d '\r'); do
  case "$file" in
    *.png|*.xml)
      adb exec-out run-as "$pkg" cat "files/acceptance/$file" > "runtime-screens/acceptance/$file"
      if [ ! -s "runtime-screens/acceptance/$file" ]; then rm -f "runtime-screens/acceptance/$file"; fi
      ;;
  esac
done
exit "$result"
