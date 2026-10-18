# Reference-accurate native rental implementation plan

> For agentic workers: use superpowers:executing-plans. Execute in this checkout; no worktree needed. User explicitly authorizes independent implementation and commits.

Goal: reproduce all ten supplied concepts in functioning native Compose screens and prove the complete simulated rental on APK.
Architecture: retain v3.4 API, fleet, LiveDemoEngine and SharedPreferences keys. Shared PremiumDesign components provide consistent styling; original standalone imagery supplies photography. Extend existing engine validation, archive receipts, and preserve navigation state.
Tech stack: Kotlin 2, Compose Material 3, osmdroid, JDK17, SDK35, Gradle8.9.
Spec: design/FULL_CODEX_PROMPT.md (supplied task specification; user message takes precedence).

## Global constraints
- Native Compose only; reference images are review inputs, never rendered as UI or sliced for assets.
- Existing fleet IDs, factual tariffs, historical storage keys and backend contracts remain compatible.
- All SMS, documents, payments, locks and vehicle telemetry remain explicitly simulated.
- Original assets for GAZelle NEXT, Transit and Largus; never substitute one model for another.
- All ten screens require real APK screenshots; compilation alone is not acceptance.

## Review focus
- Background/relaunch during active rental preserves clocks, selected car, events and route.
- Old v3.3 approval/document states migrate safely; missing newer fields have conservative defaults.
- Double actions do not duplicate booking/locking/finish; price remains consistent with catalog daily cap.
- Disabled location/network leaves useful errors and no blank blocking screen.
- Small viewport, fontScale1.3, IME and Back preserve access to pinned CTA and current trip.

### Task 1: Inventory and tool readiness
- [x] Read full specification and inspect all ten references.
- [x] Create branch from exact requested base 2362dbf; retain backend and old histories.
- [ ] Install emulator/system image, attempt software boot; record concrete limitations.
- [x] Create asset manifest before image generation; generate individual views in four image workers.

### Task 2: Engine, persistence and migration
Files: LiveDemoEngine.kt, LiveDemoStorage.kt, MainActivity.kt, tests/LiveDemoEngineTest.kt.
Interfaces: applyLive(session,event,now,value), advanceLive(session,now), demoFare(), elapsedRideSeconds(); retain existing signatures.
- [ ] Add failing regression tests for frozen receipt duration, daily cap, duplicate lock/angle commands and detailed support/incident/damage messages.
- [ ] Run gradle testDebugUnitTest --tests '*LiveDemoEngineTest*'; observe failures.
- [ ] Implement validation/frozen receipt calculations; preserve current clocks and migration.
- [ ] Archive completed sessions by bookingId; load historical receipts without overwriting current trip.
- [ ] Run full JVM suite, commit engine/persistence changes.

### Task 3: All-screen visual system
Files: PremiumDesign.kt, PremiumFleetScreen.kt, FullScreenBooking.kt, LiveDemoScreen.kt, PremiumTripDashboard.kt, AccountDashboard.kt, new PremiumGallery.kt.
Interfaces: existing screen callbacks; artworkSource(vehicle,view) maps exact IDs to six generated files.
- [ ] Theme, brand, demo notice, mint surfaces, photographic banner, lavender navigation.
- [ ] Home: hero banner, horizontal filters, map/list, synchronized markers/cards, saved filter/scroll state.
- [ ] Details/booking: six-view gallery, full-screen zoom, favorite/share, real tariff/total, pinned CTA.
- [ ] Documents: two separate photo/status/progress cards, three failures and retry, auto progression.
- [ ] Inspection: four large photographic cards, view captured photo, damage description and receipt event.
- [ ] Active/return: map, metrics, four functioning actions, incident form/chat history; return photo and condition checks, locks, pinned CTA.
- [ ] Trips/profile/settings/receipt: matching cards, archived receipts, saved settings, local support, neutral avatar.
- [ ] Build/test all screens, commit UI and assets in coherent commits.

### Task 4: Runtime acceptance and iterative visual audit
Files: design/REFERENCE_AUDIT.md, design/review/*, tests/runtime verification scripts.
- [ ] Run complete rental on installed APK, document three error/retry cases, waiting/extend, support/incident, return and rating.
- [ ] Test Back, process restart, small viewport, fontScale1.3 and rotation.
- [ ] Capture all ten screenshots and create side-by-side/diffs with original reference; inspect and fix substantial differences, recapture.
- [ ] Run backend regressions + Android JVM tests + APK signing verification; exact test counts.
- [ ] Fresh whole-branch review; address critical findings and verify.
- [ ] Commit final report with explicit remaining differences, APK/checksum/installation and branch SHA; push task branch if supported.
