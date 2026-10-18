# Reference audit — incomplete runtime acceptance

The ten original references were fully inspected before implementation. They were never used as rendered UI, sliced into assets, or submitted as generated-photo sources. Original images were generated independently and all 21 assets were inspected separately and in a contact sheet.

**Current status: NOT accepted as visually complete.** Compilation and unit tests do not demonstrate visual fidelity. Real application screenshots are pending a working Android runtime and OSM tile access. No actual.png, side-by-side.png or diff.png has been fabricated. Reference-only folders are prepared under review/.

| Reference → screen | Implemented structure | Runtime screenshot / comparison | Remaining acceptance work |
|---|---|---|---|
| 01_home_fleet → PremiumFleetScreen | Brand, notice, original photographic LIVE banner, filters, OSM map/list, synchronized vehicle selection, large photo cards | Pending | Inspect proportions/typography on APK, tiles/GPS permissions, carousel, small screen |
| 02_vehicle_details → FullScreenBooking + PremiumGallery | Six independent model-correct views, fullscreen zoom, favorite/share, specs, pickup map, catalog tariff and pinned CTA | Pending | Capture, test pinch zoom, price/layout and back from dialog |
| 03_auto_documents → LiveDemoScreen DOCUMENTS | Two original ОБРАЗЕЦ cards with progress, auto advance, three refusal reasons and retry | Pending | Capture transient progress and all three error views |
| 04_pretrip_inspection → INSPECT | Four independent model-specific angle photos, preview modal, 0–4 progress, damage description, pinned guarded CTA | Pending | Capture 2/4 and 4/4, check CTA above nav and fontScale1.3 |
| 05_active_trip → PremiumTripDashboard | Photographic heading, real OSM map with explicit virtual route, timed metrics, wait/extend/support/incident | Pending | Capture running/paused, event persistence and resume after process death |
| 06_return_flow → RETURN | OSM return map, explicit simulated zone, photo, condition confirmation, guarded lock/finish | Pending | Capture full checklist, missing-condition refusal and final close |
| 07_trips_history → TripsDashboard | Current/history segmented tabs, selected model images, true states, cancellation before start, saved event/receipt access | Pending | Capture active and empty/history data without fabricated trips |
| 08_profile → DriverDashboard | Neutral native avatar, original banner, real counters, access rows, persisted settings and local support journal | Pending | Capture each sheet, restart settings, navigation labels |
| 09_receipt_legacy → RECEIPT | Original banner, nonfiscal report, fixed time/fare, distance, zero actual charge, rating, archived receipt | Pending | Verify archive/current isolation and capture rating |
| 10_booking_legacy → FullScreenBooking | Main visual system retained for booking, quoted totals, date/time in online mode | Pending | Separate capture of tariff/consent and error/offline |

## Known deliberate differences
- Original generated photos necessarily differ from reference photography; actual three fleet models and cargo bodies are respected. No truck image substitution.
- Actual fleet tariffs/specifications override mockup inaccuracies (Transit 12 m³, daily caps, no fabricated insurance/24×7 guarantees).
- Status/navigation bars are real Android bars. No fake 9:41/LTE/100% indicators.
- Settings store the requested language preference; rental scenarios currently remain Russian. English localization is not implemented.
- Four cardinal exterior views plus hero and cargo interior satisfy the gallery. No misleading 360° button.
- All locks, document checks, vehicle positions, route and payments remain explicit local simulation.

## Tools and evidence
- `tools/compare_references.py` accepts only screenshots exported from APK execution and creates the required reference/actual/side-by-side/diff artifacts when actual screenshots exist.
- Instrumented `RentalAcceptanceTest` includes a full UI rental route and ten screenshot captures, plus receipt/archive persistence assertions. A compiled test APK is not a test result.
- The software emulator was attempted without `/dev/kvm`. First cold boot took ~447 seconds, then Android system services died and APK install returned `Broken pipe` / `Can't find service: package`. A retry uses one emulated core, more memory and `ro.hw_timeout_multiplier=10`.
- `tile.openstreetmap.org` returned HTTP CONNECT 403 under the current egress policy. Required OSM hosts have been added to the environment draft; saved draft does not mean runtime access is applied.

## Final runtime attempt result
`connectedDebugAndroidTest` failed before either instrumentation test started: AndroidTestApkInstallerPlugin could not install the target APK because Android package service was absent. Current-run XML reports tests=0, failures=0, errors=0; this is an environment setup failure, NOT a passing test run. No screenshot acceptance passed. Second ADB framebuffer capture remained black and is not presented as an application screenshot. See verification/instrumentation-result.xml and connected-tests.log.
