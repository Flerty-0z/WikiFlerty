#!/usr/bin/env bash
set -x
SCENARIO="${1:-smoke}"
OUT=footage
mkdir -p "$OUT"
PKG=com.flerty.wikiflerty

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tree() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }

adb install -r app/build/outputs/apk/release/app-universal-release.apk || exit 1
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
for f in demo/*.epub; do adb push "$f" /sdcard/Download/; done
SLOW="${SLOW:-5}"
adb shell wm density 300
adb shell settings put global animator_duration_scale "$SLOW"
adb shell settings put global transition_animation_scale "$SLOW"
adb shell settings put global window_animation_scale "$SLOW"
echo "$SLOW" > "$OUT/slow.txt"

adb shell screenrecord --bit-rate 12000000 --time-limit 170 /sdcard/rec.mp4 &
sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
tap() { python3 tools/record/ui.py tap "$1" "${2:-90}"; }
tap "Позже" 120
shot s01
tap "Пропустить" 60
sleep 10
shot s02; tree t02
adb shell input swipe 360 1200 360 400 3000
sleep 15
shot s03; tree t03
adb shell input swipe 360 400 360 1200 3000
sleep 15
shot s04
adb shell pkill -INT screenrecord
sleep 4
adb pull /sdcard/rec.mp4 "$OUT/rec.mp4"
adb shell dumpsys gfxinfo $PKG > "$OUT/gfx.txt"
adb logcat -d -t 3000 > "$OUT/logcat.txt"
ls -la "$OUT"
