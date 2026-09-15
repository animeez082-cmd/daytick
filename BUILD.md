# DayTick — build the APK (same flow as Neon Mahjong)

## One-time
1. Install Android Studio (if not already) and Node.js (already done).
2. Extract this folder anywhere, e.g. `C:\daytick`.

## Build
Open cmd in the folder (address bar → type `cmd` → Enter) and run:

    npm install
    npx cap sync android
    npx cap open android

Android Studio opens the project. Wait for Gradle to finish (bottom bar), then:

**Build → Build Bundle(s) / APK(s) → Build APK(s)**

Click "locate" in the popup → `app-debug.apk`. Send it to your phone (and your brother's) and install.

## First run on the phone
1. Allow notifications when asked.
2. Open Settings (gear icon) → tap **Allow exact alarms** → allow.
3. Tap **Send test reminder** → lock the phone → it must buzz in 10 s.
4. Phone Settings → Apps → DayTick → Battery → **Unrestricted** (so Android never kills reminders).

## Editing the app later
All the app is `www/index.html`. After any change: `npx cap sync android` → rebuild APK.
