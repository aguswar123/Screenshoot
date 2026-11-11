# GlowShot

Open-source Android app: press and hold the power button (assistant trigger) to take a screenshot with an
Apple-Intelligence style wobbly glow animation, then get a heads-up notification with **Share / Delete / Dismiss**.

## How it works
- **Trigger:** GlowShot is a `VoiceInteractionService` (default digital assistant), so the power-button long-press opens it.
- **Screenshot:** an `AccessibilityService` calls `takeScreenshot()` (Android 11+), saved to `Pictures/Screenshots`.
- **Animation:** the power button is pushed through the screen and held for 1s, then a glow flows from the button around the edges.
- **Settings:** power button side and height, glow thickness, wobble, duration, screen corner radius.

## Setup on the phone
1. Install the APK from the repository's **Releases** page.
2. Open GlowShot and tap the three setup buttons (default assistant, accessibility service, notifications).
3. Set the power-button long-press to open the assistant in your phone settings.
4. Set the power button side and height in GlowShot, then use **Test animation**.

## Build
GitHub Actions builds the APK on every push to `main` (see `.github/workflows/build.yml`).
Locally: `gradle assembleDebug` (JDK 17, Android SDK 34).

## License
MIT
