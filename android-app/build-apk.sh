#!/bin/bash
# ===========================================
# Claude Ray-Bans APK Builder
# ===========================================
# Dit script bouwt de APK automatisch.
# Vereist: Docker OF Android Studio
# ===========================================

set -e

echo "╔══════════════════════════════════════════╗"
echo "║   Claude Ray-Bans APK Builder            ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# Check voor Docker
if command -v docker &> /dev/null; then
    echo "✓ Docker gevonden - bouwen via container..."
    echo ""

    # Bouw met officiële Android Docker image
    docker run --rm -v "$(pwd)":/project -w /project \
        cimg/android:2024.01 \
        bash -c "
            echo 'SDK licenties accepteren...'
            yes | sdkmanager --licenses > /dev/null 2>&1 || true
            echo 'Bouwen van APK...'
            chmod +x gradlew 2>/dev/null || gradle wrapper
            ./gradlew assembleDebug --no-daemon
        "

    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

    if [ -f "$APK_PATH" ]; then
        echo ""
        echo "╔══════════════════════════════════════════╗"
        echo "║   ✓ APK SUCCESVOL GEBOUWD!               ║"
        echo "╚══════════════════════════════════════════╝"
        echo ""
        echo "APK locatie: $APK_PATH"
        echo "Grootte: $(du -h "$APK_PATH" | cut -f1)"
        echo ""
        echo "Kopieer dit bestand naar je Android telefoon"
        echo "en open het om te installeren."

        # Kopieer naar makkelijke locatie
        cp "$APK_PATH" ./claude-raybans.apk
        echo ""
        echo "Ook gekopieerd naar: ./claude-raybans.apk"
    else
        echo "❌ APK niet gevonden. Check de build output hierboven."
        exit 1
    fi

else
    echo "Docker niet gevonden."
    echo ""
    echo "OPTIE 1: Installeer Docker"
    echo "  macOS:  brew install --cask docker"
    echo "  Linux:  sudo apt install docker.io"
    echo "  Windows: Download Docker Desktop"
    echo ""
    echo "OPTIE 2: Gebruik Android Studio"
    echo "  1. Open Android Studio"
    echo "  2. File > Open > selecteer deze map"
    echo "  3. Build > Build Bundle(s) / APK(s) > Build APK(s)"
    echo "  4. APK staat in: app/build/outputs/apk/debug/"
    echo ""
    exit 1
fi
