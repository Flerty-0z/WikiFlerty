adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 20
snap start
