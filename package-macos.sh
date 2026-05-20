#!/usr/bin/env bash
# Build a macOS .app (and optionally .dmg) for Auto Fee Input.
#
# Requires JDK 17+ (jpackage was added in JDK 14, JavaFX 17 needs 11+;
# we use 17 to satisfy both). The bundled runtime in the .app will be
# whichever JDK runs this script.
#
# Usage:
#   ./package-macos.sh           # produces dist/AutoFeeInput.app
#   ./package-macos.sh dmg       # also produces dist/AutoFeeInput-<ver>.dmg

set -euo pipefail

APP_NAME="AutoFeeInput"
APP_VERSION="1.0.0"
JAR_NAME="auto-fee-input-${APP_VERSION}.jar"
MAIN_CLASS="com.btse.autofeeinput.Launcher"
OUT_DIR="dist"

# Pin JDK 17 (override JPACKAGE_JDK to use a different one).
JDK_HOME="${JPACKAGE_JDK:-$(/usr/libexec/java_home -v 17)}"
export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Using JDK: $(java -version 2>&1 | head -n1)"
echo "==> jpackage:  $(jpackage --version)"

echo "==> Building shaded jar"
mvn -q clean package

if [[ ! -f "target/${JAR_NAME}" ]]; then
    echo "Expected target/${JAR_NAME} but it isn't there. Check 'mvn package' output." >&2
    exit 1
fi

# Stage only what jpackage actually needs: the fat jar + JavaFX module jars.
STAGE_DIR="target/jpackage-input"
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/jfx-modules"
cp "target/${JAR_NAME}" "$STAGE_DIR/"

echo "==> Collecting JavaFX module jars"
mvn -q dependency:copy-dependencies \
    -DincludeGroupIds=org.openjfx \
    -DoutputDirectory="$STAGE_DIR/jfx-modules"

# Strip empty platform-neutral javafx jars so only the platform classifier
# (mac-aarch64 / mac-x86_64) remains.
find "$STAGE_DIR/jfx-modules" -type f -name 'javafx-*.jar' ! -name '*-mac-*' -size -1k -delete 2>/dev/null || true

echo "==> Running jpackage (app-image)"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --input "$STAGE_DIR" \
    --main-jar "$JAR_NAME" \
    --main-class "$MAIN_CLASS" \
    --module-path "$STAGE_DIR/jfx-modules" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata \
    --dest "$OUT_DIR" \
    --mac-package-name "$APP_NAME" \
    --java-options "-Xmx512m"

echo "==> Built: $OUT_DIR/$APP_NAME.app"

if [[ "${1:-}" == "dmg" ]]; then
    echo "==> Running jpackage (dmg)"
    jpackage \
        --type dmg \
        --name "$APP_NAME" \
        --app-version "$APP_VERSION" \
        --app-image "$OUT_DIR/$APP_NAME.app" \
        --dest "$OUT_DIR"
    echo "==> Built: $OUT_DIR/${APP_NAME}-${APP_VERSION}.dmg"
fi

echo
echo "Done. Open with:  open $OUT_DIR/$APP_NAME.app"
