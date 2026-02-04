# Claude Ray-Bans Assistant

Een Android app waarmee je handsfree Claude kunt gebruiken via je Meta Ray-Bans smart glasses.

## Features

- **Handsfree spraakinteractie** - Praat met Claude via je Ray-Bans
- **Automatische Bluetooth detectie** - Herkent automatisch je Meta Ray-Bans
- **Nederlandse spraakherkenning** - Optimaal voor Nederlands, met Engels als fallback
- **Conversatiegeheugen** - Claude onthoudt de context van je gesprek
- **Achtergrondservice** - Blijft actief, ook als de app geminimaliseerd is

## Bediening

### Via Meta Ray-Bans
- **Tik op touchpad**: Start/stop luisteren
- **Veeg naar voren**: Herhaal laatste antwoord
- **Veeg naar achteren**: Start nieuw gesprek

### Via de App
- **Microfoon knop**: Start/stop luisteren
- **Herhaal**: Laat Claude het laatste antwoord herhalen
- **Nieuw gesprek**: Wis de conversatiegeschiedenis

## Installatie

### Vereisten
- Android Studio Hedgehog (2023.1.1) of nieuwer
- Android SDK 34
- JDK 17

### APK Bouwen

1. Open het project in Android Studio:
   ```bash
   cd android-app
   ```

2. Bouw de debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   De APK vind je in: `app/build/outputs/apk/debug/app-debug.apk`

3. Of bouw een release APK (vereist signing):
   ```bash
   ./gradlew assembleRelease
   ```

### Direct Installeren op Telefoon

1. Sluit je Android telefoon aan via USB
2. Schakel USB debugging in op je telefoon
3. Voer uit:
   ```bash
   ./gradlew installDebug
   ```

### Handmatig Installeren

1. Kopieer de APK naar je telefoon
2. Open de APK op je telefoon
3. Sta installatie van onbekende bronnen toe indien gevraagd
4. Installeer de app

## Configuratie

1. Open de app
2. Tik op het tandwiel icoon (instellingen)
3. Voer je Anthropic API key in
4. Tik op "Opslaan"

Je kunt een API key aanmaken op: https://console.anthropic.com

## Permissies

De app vraagt om de volgende permissies:
- **Microfoon** - Voor spraakherkenning
- **Bluetooth** - Voor verbinding met Ray-Bans
- **Internet** - Voor communicatie met Claude API
- **Notificaties** - Voor de achtergrondservice indicator

## Technische Details

- **Taal**: Kotlin
- **UI**: Jetpack Compose met Material 3
- **Claude Model**: claude-sonnet-4-20250514
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Projectstructuur

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/raybans/claudeassistant/
│   │   │   ├── api/           # Claude API client
│   │   │   ├── audio/         # Speech & Bluetooth handlers
│   │   │   ├── service/       # Foreground service
│   │   │   ├── ui/            # Compose theme
│   │   │   ├── MainActivity.kt
│   │   │   └── SettingsActivity.kt
│   │   ├── res/               # Resources (layouts, strings, icons)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Troubleshooting

### Ray-Bans worden niet herkend
- Zorg dat je Ray-Bans gekoppeld zijn via Bluetooth in Android instellingen
- Controleer of de Meta View app geïnstalleerd en geconfigureerd is
- Herstart de Claude app na het koppelen

### Spraakherkenning werkt niet
- Controleer of de microfoon permissie is toegestaan
- Zorg voor een stabiele internetverbinding
- Probeer in een stillere omgeving

### Claude reageert niet
- Controleer of je API key correct is ingevoerd
- Controleer je internetverbinding
- Check of je Anthropic account actief is

## Licentie

MIT License
