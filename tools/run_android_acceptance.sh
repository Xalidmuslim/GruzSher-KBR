#!/usr/bin/env bash
# Keep the Gradle exit status, but collect real emulator screenshots regardless.
set +e
if [[ "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v388-premium-120hz-motion" ]]; then
  # Check map/list Back navigation and fixed booking footer after smooth transitions.
  gradle -p android --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.kbr.cargoshare.MapListBackNavigationTest,ru.kbr.cargoshare.CompactLayoutTest
elif [[ "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v387-selected-icon-back-to-map" ]]; then
  gradle -p android --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.kbr.cargoshare.MapListBackNavigationTest
elif [[ "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v386-compact-cards-safe-footer" ]]; then
  # Pixel-size QA of the actual selected truck card and clipped booking CTA,
  # plus a live settings font-size regression at 100–150%.
  gradle -p android --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.kbr.cargoshare.CompactLayoutTest,ru.kbr.cargoshare.FontScaleLiveTest
elif [[ "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v384-live-global-font-scale" ||
      "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v385-gallery-layout-icon-font" ]]; then
  gradle -p android --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.kbr.cargoshare.FontScaleLiveTest
elif [[ "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v381-map-vehicle-card-layout-audit" ||
      "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v382-card-route-font-safeareas" ||
      "${GITHUB_REF_NAME:-}" == "feature/gruzsher-v383-booking-footer-safe-insets" ]]; then
  # Dedicated layout QA: capture phone-sized real APK frames without requiring
  # the legacy screenshot-reference labels to remain identical.
  gradle -p android --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.kbr.cargoshare.LayoutSmokeTest
else
  gradle -p android --no-daemon connectedDebugAndroidTest
fi
result=$?
mkdir -p runtime-screens/acceptance
echo "Collecting actual APK screenshots and UI diagnostics..."
adb pull /sdcard/Download/GruzSherQA/. runtime-screens/acceptance/ || true
exit "$result"
