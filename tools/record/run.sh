#!/usr/bin/env bash
set -x
SCENARIO="${1:-smoke}"
OUT=footage
mkdir -p "$OUT"
PKG=com.flerty.wikiflerty

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tree() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }

adb install -r app/build/outputs/apk/release/app-universal-release.apk || exit 1
adb root >/dev/null 2>&1; sleep 3
adb shell "setprop persist.sys.locale ru-RU; setprop ctl.restart zygote"
sleep 20
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 10
adb shell mkdir -p /sdcard/Books
for f in demo/*; do adb push "$f" /sdcard/Books/; done
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Books >/dev/null 2>&1
SLOW="${SLOW:-5}"
adb shell wm density 300
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

adb shell settings put global animator_duration_scale "$SLOW"
adb shell settings put global transition_animation_scale "$SLOW"
adb shell settings put global window_animation_scale "$SLOW"
echo "$SLOW" > "$OUT/slow.txt"

tap() { python3 tools/record/ui.py tap "$1" "${2:-60}"; }
wait_for() { python3 tools/record/ui.py find "$1" "${2:-60}"; }
swipe() { adb shell input swipe $1 $2 $3 $4 "${5:-600}"; }
rec_start() { adb shell "screenrecord --bit-rate 12000000 --time-limit ${2:-170} /sdcard/$1.mp4" & REC_PID=$!; sleep 1; }
rec_stop() { adb shell pkill -INT screenrecord; wait $REC_PID 2>/dev/null; sleep 2; adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4"; adb shell rm -f "/sdcard/$1.mp4"; }
step=0
snap() { step=$((step+1)); n=$(printf "%02d_%s" $step "$1"); shot "$n"; tree "$n"; }

source "tools/record/scenes/$SCENARIO.sh"

adb shell dumpsys gfxinfo $PKG > "$OUT/gfx.txt"
adb logcat -d -t 4000 > "$OUT/logcat.txt"
ls -la "$OUT"
