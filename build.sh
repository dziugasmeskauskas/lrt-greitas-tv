#!/bin/bash
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
# Newest installed build-tools and platform unless overridden
BT="${ANDROID_BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)}"
BT="${BT%/}"
PLATFORM_DIR="$(ls -d "$SDK"/platforms/android-*/ | sort -V | tail -1)"
PLATFORM="${ANDROID_PLATFORM_JAR:-${PLATFORM_DIR%/}/android.jar}"
# Kept outside the repo so a fresh clone can still update the installed app (same signature)
KEYSTORE="${LRT_KEYSTORE:-$HOME/.android/lrt-greitas.keystore}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$PROJ/build"
mkdir -p "$PROJ/build/gen" "$PROJ/build/obj" "$PROJ/build/dex"

echo "1. Compiling resources with aapt2..."
"$BT/aapt2" compile --dir "$PROJ/res" -o "$PROJ/build/res.zip"
"$BT/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$PROJ/build/gen" \
  -o "$PROJ/build/unaligned.apk" \
  "$PROJ/build/res.zip"

echo "2. Compiling Java sources with javac..."
"$JAVA_HOME/bin/javac" \
  -source 11 -target 11 \
  -cp "$PLATFORM" \
  -d "$PROJ/build/obj" \
  "$PROJ/build/gen/lt/lrt/lean/R.java" \
  "$PROJ/src/lt/lrt/lean/"*.java

echo "3. Converting bytecode to DEX with d8..."
"$BT/d8" \
  --min-api 26 \
  --lib "$PLATFORM" \
  --output "$PROJ/build/dex" \
  "$PROJ/build/obj/lt/lrt/lean/"*.class

echo "4. Packaging classes.dex into APK..."
cd "$PROJ/build/dex"
zip -q -u "$PROJ/build/unaligned.apk" classes.dex

echo "5. Aligning APK with zipalign..."
"$BT/zipalign" -f -p 4 "$PROJ/build/unaligned.apk" "$PROJ/build/aligned.apk"

echo "6. Generating signing keystore (first run only) & signing APK..."
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  "$JAVA_HOME/bin/keytool" -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BT/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$PROJ/lrt-greitas.apk" \
  "$PROJ/build/aligned.apk"

ls -lh "$PROJ/lrt-greitas.apk"
[ -n "$NO_INSTALL" ] && exit 0

echo "7. Installing and launching LRT Greitas on Philips TV..."
adb install -r "$PROJ/lrt-greitas.apk"
adb shell cmd package compile -m speed -f lt.lrt.lean
adb shell am start-foreground-service -n lt.lrt.lean/.BtKeepAliveService --ez bounce_bt true
adb shell am start -n lt.lrt.lean/.MainActivity
echo "DEPLOYED AND LAUNCHED!"
