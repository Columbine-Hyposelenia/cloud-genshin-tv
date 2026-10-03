#!/bin/bash
set -e

export JAVA_HOME=/home/user/Doubao/chats/38445428397619970/jdk-17.0.12+7
export ANDROID_HOME=/home/user/Doubao/chats/38445428397619970/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/33.0.2:$PATH

PROJECT_DIR="/home/user/Doubao/chats/38445428397619970/cloud-genshin-tv"
ANDROID_JAR="$ANDROID_HOME/platforms/android-22/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/33.0.2"

KEYSTORE="$PROJECT_DIR/debug.keystore"
KEYSTORE_PASS="android"
KEY_ALIAS="androiddebugkey"

mkdir -p "$PROJECT_DIR/build"

if [ ! -f "$KEYSTORE" ]; then
    echo "Creating debug keystore..."
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=CloudGenshin, OU=Dev, O=CloudGenshin, L=Shenzhen, ST=Guangdong, C=CN" \
        2>&1 | tail -3
fi

build_module() {
    local MODULE=$1
    local PACKAGE=$2
    echo "========================================="
    echo "Building $MODULE ($PACKAGE)..."
    echo "========================================="

    local MODULE_DIR="$PROJECT_DIR/$MODULE"
    local BUILD_DIR="$PROJECT_DIR/build/$MODULE"
    local GEN_DIR="$BUILD_DIR/gen"
    local OBJ_DIR="$BUILD_DIR/obj"
    local APK_DIR="$BUILD_DIR/apk"

    rm -rf "$BUILD_DIR"
    mkdir -p "$GEN_DIR" "$OBJ_DIR" "$APK_DIR"

    echo "[1/5] Compiling resources..."
    aapt2 compile --dir "$MODULE_DIR/res" -o "$BUILD_DIR/compiled_res.zip" 2>&1

    echo "[2/5] Linking resources..."
    aapt2 link -I "$ANDROID_JAR" \
        --manifest "$MODULE_DIR/AndroidManifest.xml" \
        -R "$BUILD_DIR/compiled_res.zip" \
        --java "$GEN_DIR" \
        --auto-add-overlay \
        -o "$APK_DIR/resources.ap_" 2>&1

    echo "[3/5] Compiling Java..."
    find "$MODULE_DIR/src" "$GEN_DIR" -name "*.java" > "$BUILD_DIR/sources.txt"
    javac -source 1.8 -target 1.8 \
        -bootclasspath "$ANDROID_JAR" \
        -classpath "$ANDROID_JAR" \
        -d "$OBJ_DIR" \
        @"$BUILD_DIR/sources.txt" 2>&1

    echo "[4/5] Converting to DEX..."
    find "$OBJ_DIR" -name "*.class" > "$BUILD_DIR/classes.txt"
    d8 --min-api 21 \
        --lib "$ANDROID_JAR" \
        --output "$APK_DIR" \
        @"$BUILD_DIR/classes.txt" 2>&1

    echo "[5/5] Packaging and signing APK..."
    cd "$APK_DIR"
    cp resources.ap_ "$MODULE-unsigned.apk"
    zip -u "$MODULE-unsigned.apk" classes.dex 2>&1 | tail -1
    cd "$PROJECT_DIR"

    zipalign -f 4 "$APK_DIR/$MODULE-unsigned.apk" "$APK_DIR/$MODULE-aligned.apk" 2>&1

    apksigner sign --ks "$KEYSTORE" \
        --ks-pass "pass:$KEYSTORE_PASS" \
        --key-pass "pass:$KEYSTORE_PASS" \
        --ks-key-alias "$KEY_ALIAS" \
        --out "$PROJECT_DIR/build/$MODULE-release.apk" \
        "$APK_DIR/$MODULE-aligned.apk" 2>&1

    local APK_SIZE=$(stat -c%s "$PROJECT_DIR/build/$MODULE-release.apk" 2>/dev/null || stat -f%z "$PROJECT_DIR/build/$MODULE-release.apk" 2>/dev/null)
    echo ""
    echo "SUCCESS: $MODULE-release.apk ($((APK_SIZE / 1024)) KB)"
    echo "Path: $PROJECT_DIR/build/$MODULE-release.apk"
    echo ""
}

build_module "installer" "com.cloudgenshin.installer"
build_module "app" "com.cloudgenshin.tv"

echo "========================================="
echo "Build complete!"
echo "========================================="
ls -la "$PROJECT_DIR/build/"*.apk
