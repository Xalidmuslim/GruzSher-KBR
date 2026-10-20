# AGENTS.md — GruzSher KBR working rules

## Owner-approved stable baseline (mandatory)

Before ANY future feature, UI, maintenance, performance or refactor changes, read
**[docs/ACCEPTED_OPTIMIZATION_BASELINE.md](docs/ACCEPTED_OPTIMIZATION_BASELINE.md)**.

The owner explicitly confirmed on 2026-10-20 that the app optimization is successful
and requested that the accepted parameters **must not be changed by subsequent edits**.

The accepted source version is **3.8.9** at commit
`7e602d62e456c278093f3df006d00c849984e5b7`;
CI evidence: https://github.com/Xalidmuslim/GruzSher-KBR/actions/runs/39454030269.

This is a non-regression contract, not permission to alter any other project.

### Non-negotiable unless the owner explicitly requests otherwise

- Preserve optimized native Compose rendering, deferred first-screen asset preload,
  image memory/cache strategy, lazy lists, and tuned short transitions.
- Do not reintroduce heavy double map composition, slow cold start, janky map swipes,
  LIVE timer-driven fleet recompositions, long waiting screens or unnecessary redraws.
- Keep Android R8 release shrink/optimization and release performance checks.
- Keep system Back **list → map**, then normal navigation; retain selected truck
  details and road-route controls.
- Keep the minimalist selected mountain/GAZelle launcher icon, agreed matte visual
  palette, compact cards and safe, non-clipped booking footer.
- Preserve default 110% text, live options 100–150%, user settings, app functionality,
  and correct behavior at Android gesture-safe areas.
- Never silently substitute a debug build, unpublished APK, or production backend
  when testing the user-approved offline preview.

### Verification for every change

1. Keep the accepted performance and UI behavior as the reference.
2. Run appropriate tests and a build; where graphics/navigation change, run
   on-device/emulator UI checks at small/large fonts and gesture navigation.
3. For performance-sensitive edits, compare against the accepted R8 release
   performance evidence and do not assert no regression without measurement.
4. If a requested change conflicts with this baseline, surface the trade-off
   clearly rather than silently overriding the baseline.
