# Accepted optimization baseline — GruzSher KBR

Status: **ACCEPTED BY OWNER / DO NOT REGRESS**  
Accepted: **2026-10-20**  
Baseline branch: `feature/gruzsher-v389-native-perf-r8-release`  
Accepted code commit (before adding this document): `7e602d62e456c278093f3df006d00c849984e5b7`  
Verified GitHub Actions run: https://github.com/Xalidmuslim/GruzSher-KBR/actions/runs/39454030269

## Owner's standing instruction

**The user confirmed that optimization was successful. Preserve the accepted speed, smoothness, design, accessibility and navigation behavior whenever modifying this app.** Do not change the accepted values below as an incidental consequence of an unrelated design, feature or bug-fix task. Changes to these behaviors require the owner's *explicit request*, plus regression checks. New features must be integrated without removing existing functions.

This baseline documents accepted implementation behavior; it is **not** a guarantee of identical benchmark numbers on every phone. For metrics, compare the release emulator evidence from the linked CI run to the next release with the same environment.

## Preserve native performance

1. **Startup / image loading**: Initial UI composition must not wait for decoding. Keep `FleetImagePreloader.enqueueAboveTheFold()` deferred until after first frame (currently launched after **500 ms** in `MainActivity`). Shared Coil loader, background worker decode, memory cache enabled and disk cache disabled for preloaded local assets. Preload **only four** first-screen assets: `live_banner.webp`, `gazelle_hero.webp`, `transit_hero.webp`, `largus_hero.webp`; target size **620 × 400**. Do **not** eagerly decode the entire multi-photo gallery or instantiate extra loaders/GPU layers per image.
2. **Animations**: Keep a single short GPU-layer transition, **165 ms** for main tab entry and **145 ms** for map/list switching (unless user approves different timings). Do not cross-compose both heavy maps, build unnecessary `AnimatedContent` trees or animate whole-screen geometry that causes jank.
3. **List/map state**: Preserve lazy rendering, cached fleet filtering via `remember`, proper state restoration, responsive OSMDroid and MapLibre gestures, road-route layers behind GPS/truck markers, and Android edge-back. List-mode system Back restores the embedded **map mode** instead of abandoning the fleet section.
4. **Simulator clocks**: Avoid making second-by-second LIVE Demo changes recompose or refilter the vehicle catalog; retain `LiveClockPolicy` behavior and related tests.
5. **Release optimization**: In the R8 **release** build retain `isMinifyEnabled=true`, `isShrinkResources=true`, `proguard-android-optimize.txt` and `proguard-rules.pro`. Current Gradle build-process cap: `-Xmx3g -XX:MaxMetaspaceSize=768m`. **These are build-time JVM settings, not app runtime RAM.** Retain Android/Compose/MapLibre compatibility while adjusting rules. The current release uses a **debug signature for testing only**, not a production signing key.
6. **Verification**: Preserve the scripts and workflow `tools/run_android_performance.sh` and `.github/workflows/android-debug.yml`. When touching startup, rendering, maps, animations, image loaders or R8 settings, build an installable release APK, run automated checks, and compare cold start, scroll, transitions, memory and crash behavior against the approved baseline before claiming no regression.

## Preserve accepted UI/UX

- Brand: modern restrained green/cream, **matte**, premium minimalism; no unnecessary gradients, bright oversaturation or decorative extra borders. Keep the selected minimalist launcher icon with KBR mountainous motif and 2012-era GAZelle reference as approved.
- Fleet modes: retain interactive map, list/carousel, and **Back from list to preceding map**. Truck tap shows full details and route controls. Routes are by streets where available and preserve simulator/GPS disclaimer.
- Gallery: maintain native single-finger photo swipes and fullscreen pager, thumb taps and arrow actions.
- Cards / safe areas: compact truck cards (no extra weighted blank middle), compact fixed booking footer; bottom green CTA must be fully visible ABOVE Android gesture navigation, including large fonts.
- Typography: app default **110%**; user choices **100, 110, 120, 130, 140, 150%**. Apply visibly and immediately across app screens and open settings; preserve selected value. Material-style layouts must remain usable at 150%. This does not rescale OpenStreetMap raster tile lettering.
- All existing functions: truck photos, prices, vehicle details, route button, LIVE Demo flow, profiles, history, booking/returns and user settings must survive unrelated refactors.

## Change policy for future edits

1. Read `AGENTS.md` and this baseline before editing a performance-sensitive file.
2. Change only code relevant to the user's request. Do not reset performance parameters, replace native components with WebViews, switch map engines, redesign branding, or silently discard validated user preferences.
3. Build/test; for substantial UI changes include visual emulator smoke checks and safe-area/accessibility-size coverage.
4. If the requested feature truly requires altering a protected optimization, explain the trade-off, ask permission when necessary and create a reviewable separate change. Never claim a benchmark or test passed without evidence.
5. Preserve the accepted commit and CI run as reproducible rollback references.

**Scope:** This file protects GruzSher-KBR. For other repositories, apply the same non-regression principle, but record their own verified baseline and values rather than copying this app's particular constants blindly.
