#!/usr/bin/env bash
# Device-side measurements using the installable R8 Release APK. Run inside
# reactivecircus Android emulator action; no source-code instrumentation.
set -euo pipefail
APK="android/app/build/outputs/apk/release/app-release.apk"
PACKAGE="ru.kbr.cargoshare.referencepreview.ux388"
ACTIVITY="$PACKAGE/ru.kbr.cargoshare.MainActivity"
OUT="runtime-screens/performance"
mkdir -p "$OUT"
test -s "$APK"
adb wait-for-device
adb install -r "$APK"
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
adb shell input keyevent 3
echo "Android build: $(adb shell getprop ro.build.version.release)" > "$OUT/metrics.txt"
echo "Hardware: $(adb shell getprop ro.product.model)" >> "$OUT/metrics.txt"
echo "Package: $PACKAGE" >> "$OUT/metrics.txt"
echo "Mode: R8 release; offline demo; system animations preserved at 1.0x" >> "$OUT/metrics.txt"
echo >> "$OUT/metrics.txt"
echo "Cold-start times (am force-stop + am start -W; milliseconds):" >> "$OUT/metrics.txt"
: > "$OUT/cold-starts.txt"
for i in 1 2 3 4 5; do
  adb shell am force-stop "$PACKAGE"
  # User assets are kept warm in the OS filesystem cache between starts, as on
  # a real repeatedly-launched phone. Each attempt kills the app process.
  adb shell am start -W -n "$ACTIVITY" > "$OUT/launch-$i.txt"
  awk '/TotalTime:/{print $2}' "$OUT/launch-$i.txt" >> "$OUT/cold-starts.txt"
  sleep 2
done
python3 - <<'PY' | tee -a "$OUT/metrics.txt"
import statistics
from pathlib import Path
data = [int(s) for s in Path("runtime-screens/performance/cold-starts.txt").read_text().split() if s.isdigit()]
if not data:
    raise SystemExit("FAIL: no Android cold start measurements")
print("Samples:", ", ".join(map(str, data)))
print("Median cold start:", statistics.median(data), "ms")
print("Min/Max:", min(data), "/", max(data), "ms")
PY
adb shell dumpsys meminfo "$PACKAGE" > "$OUT/meminfo.txt"
adb shell screencap -p /sdcard/gruzsher_performance_home.png
adb pull /sdcard/gruzsher_performance_home.png "$OUT/home.png" > /dev/null

# Real input and dumpsys frame measurements on the started APK.
SCREEN=$(adb shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
SW=${SCREEN%x*}
SH=${SCREEN#*x}
if [[ ! "${SW}" =~ ^[0-9]+$ || ! "${SH}" =~ ^[0-9]+$ ]]; then
  echo "Could not determine emulator display size" >&2
  exit 1
fi
adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null
for i in 1 2 3 4 5 6; do
  adb shell input swipe $((SW/2)) $((SH*78/100)) $((SW/2)) $((SH*35/100)) 410
  adb shell input swipe $((SW/2)) $((SH*32/100)) $((SW/2)) $((SH*75/100)) 410
done
sleep 2
adb shell dumpsys gfxinfo "$PACKAGE" > "$OUT/scroll-gfxinfo.txt"
adb shell screencap -p /sdcard/gruzsher_performance_scroll.png
adb pull /sdcard/gruzsher_performance_scroll.png "$OUT/scroll.png" > /dev/null

adb shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null
for i in 1 2 3; do
  # Preserve real Android OS animation speed; use bottom native navigation.
  adb shell input tap $((SW*36/100)) $((SH*94/100))
  sleep 0.45
  adb shell input tap $((SW*61/100)) $((SH*94/100))
  sleep 0.45
  adb shell input tap $((SW*11/100)) $((SH*94/100))
  sleep 0.45
done
adb shell dumpsys gfxinfo "$PACKAGE" > "$OUT/transitions-gfxinfo.txt"
adb shell screencap -p /sdcard/gruzsher_performance_transitions.png
adb pull /sdcard/gruzsher_performance_transitions.png "$OUT/transitions.png" > /dev/null

python3 - <<'PY' | tee -a "$OUT/metrics.txt"
import re
from pathlib import Path
base = Path("runtime-screens/performance")
for title, file in [("Home-list scroll", "scroll-gfxinfo.txt"), ("Bottom-tab transitions", "transitions-gfxinfo.txt")]:
    text = (base / file).read_text(errors="ignore")
    frame = re.search(r"Total frames rendered:\s*(\d+)", text)
    jank = re.search(r"Janky frames:\s*(\d+)\s*\(([^)]+)\)", text)
    print(f"{title}:")
    print("  Frames:", frame.group(1) if frame else "not reported by device")
    print("  Jank:", f"{jank.group(1)} ({jank.group(2)})" if jank else "not reported by device")
PY
PID=$(adb shell pidof "$PACKAGE" | tr -d '\r')
test -n "$PID" || { echo 'FAIL: release app process crashed during UI tests' >&2; exit 1; }
adb logcat -d -t 2000 -v brief | grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $PACKAGE" > "$OUT/fatal-log-scan.txt" || true
if grep -q "FATAL EXCEPTION" "$OUT/fatal-log-scan.txt"; then
  echo 'FAIL: Android runtime reported FATAL EXCEPTION' >&2
  tail -30 "$OUT/fatal-log-scan.txt"
  exit 1
fi
echo "Result: App remains alive after 5 cold launches, 12 scroll gestures, and 9 bottom-tab changes." | tee -a "$OUT/metrics.txt"
cat "$OUT/metrics.txt"
