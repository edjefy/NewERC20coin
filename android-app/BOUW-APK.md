# 🔨 APK Bouwen - Snelstart

## Snelste methode: Android Studio

### Stap 1: Download Android Studio
Download van: https://developer.android.com/studio

### Stap 2: Open het project
1. Start Android Studio
2. Klik **"Open"**
3. Selecteer de `android-app` map
4. Wacht tot Gradle sync klaar is (2-5 minuten eerste keer)

### Stap 3: Bouw de APK
1. Menu: **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
2. Wacht tot "BUILD SUCCESSFUL" verschijnt
3. Klik op **"locate"** in de popup

### Stap 4: Installeer op je telefoon
De APK staat in:
```
app/build/outputs/apk/debug/app-debug.apk
```

Stuur dit bestand naar je telefoon (email, Google Drive, USB kabel) en open het.

---

## Alternatief: Command Line met Docker

```bash
cd android-app
chmod +x build-apk.sh
./build-apk.sh
```

De APK wordt gekopieerd naar `claude-raybans.apk`

---

## Na installatie

1. Open de app
2. Tik op ⚙️ (instellingen)
3. Vul je API key in van https://console.anthropic.com
4. Verbind je Ray-Bans via Bluetooth
5. Tik op je Ray-Bans en praat!
