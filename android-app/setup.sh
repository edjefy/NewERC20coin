#!/bin/bash
# Setup script for Claude Ray-Bans Assistant

echo "=== Claude Ray-Bans Assistant Setup ==="
echo ""

# Check if gradle is installed
if ! command -v gradle &> /dev/null; then
    echo "Gradle is not installed. Please install Gradle first:"
    echo "  macOS: brew install gradle"
    echo "  Linux: sudo apt install gradle"
    echo "  Windows: Download from https://gradle.org/install/"
    exit 1
fi

echo "Generating Gradle wrapper..."
gradle wrapper --gradle-version 8.4

echo ""
echo "Setup complete! You can now build the app:"
echo ""
echo "  Debug APK:   ./gradlew assembleDebug"
echo "  Release APK: ./gradlew assembleRelease"
echo "  Install:     ./gradlew installDebug"
echo ""
echo "The APK will be in: app/build/outputs/apk/"
