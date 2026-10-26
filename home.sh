#!/bin/bash
# Build, test and install the home screen with the SDK's own tools (no Gradle).
#   ./home.sh build | test | install | home | panel-key | restore      (adb target: ANDROID_SERIAL)
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-34/android.jar"
PKG=io.github.caferyukseloglu.tvhome
APK=dist/android-tv-home.apk
KEYSTORE=.signing/local.keystore
# Stock launchers that some TVs force over the user's choice of home app.
LAUNCHERS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"

build() {
    rm -rf build && mkdir -p build/classes build/dex dist
    "$BT/aapt2" compile --dir res -o build/res.zip
    "$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen build/res.zip
    javac -encoding UTF-8 --release 8 -Xlint:-options -classpath "$JAR" -d build/classes $(find src build/gen -name '*.java')
    "$BT/d8" --lib "$JAR" --min-api 26 --output build/dex $(find build/classes -name '*.class')
    (cd build/dex && zip -q -u ../unsigned.apk classes.dex)
    "$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
    if [ ! -f "$KEYSTORE" ]; then
        mkdir -p .signing
        keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias local \
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=android-tv-home local build"
    fi
    "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias local --out "$APK" build/aligned.apk
    ls -lh "$APK"
}

run_tests() {
    mkdir -p build/test
    # Only the pure-Java classes: they run on the Mac's JVM without Android.
    javac -d build/test src/io/github/caferyukseloglu/tvhome/{AppOrder,Colors}.java tools/*Test.java
    java -cp build/test "$PKG.AppOrderTest"
    java -cp build/test "$PKG.ColorsTest"
}

install() {
    build
    adb install -r "$APK"
}

# Make this the home screen. If the TV still opens its own launcher, switch that launcher off
# (pm disable-user keeps it installed with its data) and remember it for `restore`.
home() {
    adb shell cmd package set-home-activity "$PKG/.MainActivity"
    adb shell input keyevent KEYCODE_HOME
    sleep 2
    if adb shell dumpsys activity activities | grep -q "mResumedActivity.*$PKG"; then
        echo "Home is set."
        return
    fi
    mkdir -p .local
    for launcher in $LAUNCHERS; do
        if adb shell pm list packages -e | grep -qx "package:$launcher"; then
            adb shell pm disable-user --user 0 "$launcher"
            echo "$launcher" >> .local/disabled-launchers.txt
        fi
    done
    adb shell input keyevent KEYCODE_HOME
}

# One remote key opens the lights panel from anywhere (an accessibility service that sees keys only).
panel_key() {
    local service="$PKG/.PanelKey"
    # TCL blocks third-party services from being bound unless this TCL-only app op allows it.
    adb shell appops set "$PKG" APP_AUTO_START allow 2>/dev/null || true
    local enabled
    enabled=$(adb shell settings get secure enabled_accessibility_services)
    if [[ "$enabled" == "null" || -z "$enabled" ]]; then enabled="$service"
    elif [[ "$enabled" != *"$service"* ]]; then enabled="$enabled:$service"; fi
    adb shell settings put secure enabled_accessibility_services "$enabled"
    adb shell settings put secure accessibility_enabled 1
}

restore() {
    if [ -f .local/disabled-launchers.txt ]; then
        while read -r launcher; do adb shell pm enable --user 0 "$launcher"; done < .local/disabled-launchers.txt
        rm .local/disabled-launchers.txt
    fi
    # Hand Home to the first stock launcher this TV has, by its real HOME activity.
    for launcher in $LAUNCHERS; do
        component=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME "$launcher" | tail -1)
        if [[ "$component" == */* ]]; then
            adb shell cmd package set-home-activity "$component"
            break
        fi
    done
    adb shell input keyevent KEYCODE_HOME
}

case "${1:-}" in
    build) build ;;
    test) run_tests ;;
    install) install ;;
    home) home ;;
    panel-key) panel_key ;;
    restore) restore ;;
    *) echo "usage: $0 build|test|install|home|panel-key|restore"; exit 1 ;;
esac
