# Incognito Blocker - Android App

An Android app that detects when you open an incognito tab in Chrome and automatically closes Chrome.

## Features

- **Accessibility Service** - Monitors Chrome for incognito tab indicators
- **Automatic Detection** - Detects incognito mode via UI elements, content descriptions, and text
- **Force Close Chrome** - Attempts to close Chrome when incognito is detected
- **Foreground Service** - Keeps monitoring active in background
- **Boot Persistence** - Restarts monitoring after device reboot
- **Notifications** - Alerts you when incognito is detected
- **Statistics** - Tracks detection count and last detection time

## How It Works

1. **Accessibility Service** monitors Chrome's UI for incognito indicators:
   - "Incognito" text in toolbar/content descriptions
   - Incognito icon (hat/glasses icon)
   - Private browsing related view IDs
   - Class names containing "incognito"

2. **When detected**, the app:
   - Shows a high-priority notification
   - Attempts to kill Chrome's background processes
   - Tries to click close/back buttons via accessibility
   - Sends Chrome to background

3. **Foreground Service** keeps the app process alive for continuous monitoring

## Requirements

- Android 7.0 (API 24) or higher
- Accessibility Service permission (user must enable manually)
- Notification permission (Android 13+)

## Installation & Setup

### Option 1: Build from Source using Android Studio (Recommended ⭐)

> **Important**: The command line build may fail if you have Java 25+ installed. Android Studio includes its own compatible JDK (Java 17/21) and will work out of the box.

1. **Install Android Studio**
   - Download from [developer.android.com](https://developer.android.com/studio)
   - Install with default settings (includes Android SDK and JDK 17)

2. **Open Project**
   ```
   File → Open → Select the chrome-et folder
   ```
   - Android Studio will auto-download Gradle and dependencies
   - Wait for "Gradle Sync Finished" (bottom right)

3. **Build APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   - Click "locate" in the notification to find the APK

4. **Install on Device**
   - Connect phone via USB with **USB Debugging enabled** (Settings → Developer Options)
   - Or transfer `app/build/outputs/apk/debug/app-debug.apk` to phone
   - Install APK (allow "Install unknown apps" if prompted)

### Option 2: Command Line Build (Requires Java 17 or 21)

If you have **Java 17 or 21** installed (not Java 25+):

```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
chmod +x gradlew
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

**If build fails with "Unsupported class file major version 69"**:
- You have Java 25+ which is not supported by Gradle yet
- **Use Android Studio instead** (Option 1)
- Or install Java 17/21 and set `JAVA_HOME` to it

## Configuration Steps (After Install)

### 1. Enable Accessibility Service (REQUIRED)

1. Open **Settings** → **Accessibility**
2. Find **"Incognito Tab Detector"** (or "Incognito Blocker")
3. Tap it and **enable the toggle**
4. Confirm "Allow" on the permission dialog

### 2. Grant Notification Permission (Android 13+)

1. Open app → Tap **"Start Monitoring"**
2. Allow notifications when prompted

### 3. Start Monitoring

1. Open the app
2. Tap **"Start Monitoring"**
3. Status should show "Monitoring Active"

## Testing

1. Open Chrome
2. Tap menu (⋮) → **New incognito tab**
3. You should see:
   - Notification: "Incognito Tab Detected"
   - Chrome closes or minimizes
   - App shows "Last detection: Xs ago"

## Limitations & Known Issues

### Android 10+ Restrictions
- **Force-closing apps is restricted** on Android 10+
- `killBackgroundProcesses()` only works for your own apps
- The app uses alternative methods (accessibility clicks, home intent)
- May not fully close Chrome on newer Android versions

### Workarounds
- **Root access**: If rooted, can use `su -c "am force-stop com.android.chrome"`
- **Device Owner Mode**: Enterprise provisioning allows full app control
- **Automation apps**: Tasker/MacroDroid with root can force-close reliably

### Detection Accuracy
- Works best when Chrome UI is in English
- May miss incognito if Chrome UI changes
- False positives possible on similar "private" UI

## Permissions Explained

| Permission | Purpose |
|------------|---------|
| `BIND_ACCESSIBILITY_SERVICE` | Monitor Chrome UI for incognito |
| `FOREGROUND_SERVICE` | Keep monitoring alive in background |
| `KILL_BACKGROUND_PROCESSES` | Attempt to close Chrome (limited on Android 10+) |
| `POST_NOTIFICATIONS` | Show alerts when incognito detected |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `QUERY_ALL_PACKAGES` | Detect Chrome package variants |

## Project Structure

```
chrome-et/
├── app/
│   ├── src/main/
│   │   ├── java/com/incognitoblocker/
│   │   │   ├── IncognitoBlockerApplication.kt  # App class with prefs
│   │   │   ├── IncognitoAccessibilityService.kt # Core detection logic
│   │   │   ├── MonitoringForegroundService.kt   # Background service
│   │   │   ├── BootReceiver.kt                  # Auto-start on boot
│   │   │   └── MainActivity.kt                  # Main UI
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml         # Main UI layout
│   │   │   ├── xml/accessibility_service_config.xml # Accessibility config
│   │   │   ├── values/strings.xml               # Text resources
│   │   │   ├── values/colors.xml                # Colors
│   │   │   └── values/themes.xml                # Themes
│   │   └── AndroidManifest.xml                  # Permissions & components
│   └── build.gradle.kts                         # App build config
├── build.gradle.kts                             # Project build config
├── settings.gradle.kts                          # Project settings
├── gradlew / gradlew.bat                        # Gradle wrapper
└── README.md                                    # This file
```

## Customization

### Modify Detection Keywords
Edit `IncognitoAccessibilityService.kt`:
```kotlin
private val incognitoKeywords = listOf(
    "incognito",
    "private",
    "inprivate",
    // Add more keywords for other languages/browsers
)
```

### Add More Browsers
Add package names to `chromePackages` set:
```kotlin
private val chromePackages = setOf(
    "com.android.chrome",
    "com.chrome.beta",
    // Add: "org.mozilla.firefox", "com.microsoft.emmx", etc.
)
```

### Adjust Debounce Time
```kotlin
private val CHECK_DEBOUNCE_MS = 2000L  // Check every 2 seconds max
```

## Troubleshooting

### "Accessibility Service not found"
- Rebuild and reinstall app
- Restart phone
- Check Settings → Accessibility → Downloaded services

### "Monitoring won't start"
- Ensure Accessibility Service is enabled
- Grant notification permission
- Check battery optimization: Settings → Battery → App optimization → Don't optimize

### "Chrome doesn't close"
- Android 10+ restriction (see Limitations)
- Try accessibility click method in service logs
- Consider using with automation app (Tasker, MacroDroid)

### View Logs
```bash
# Filter for app logs
adb logcat | grep -i incognito

# Or in Android Studio Logcat filter: "IncognitoAccessibility"
```

## Building Release Version

```bash
./gradlew assembleRelease
```

Sign the APK before distribution:
```bash
# Generate keystore (once)
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

# Sign APK
apksigner sign --ks my-release-key.jks --out app-release-signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## License

MIT License - Feel free to modify and distribute.

## Contributing

1. Fork the repository
2. Create feature branch
3. Submit pull request

## Support

For issues:
1. Check Logcat for errors
2. Verify Accessibility Service is enabled
3. Test on different Android versions
4. Report with device model, Android version, and logs