# Calculator Vault

A normal third-party Android app (not a launcher): a working calculator with a PIN-protected private vault.
Target device: Huawei Y7 2019, Android 9, EMUI 9.1.0 (minSdk 26, targetSdk 28, Kotlin, Java 17).

## Using the app
1. Menu (three dots) -> **Set up vault PIN** (4-8 digits, entered twice).
2. Type the PIN on the calculator keypad and press **=** to open the vault.
3. The vault locks when you leave it, switch apps, or the screen turns off.

## Build option A: Android Studio
1. Install Android Studio (Hedgehog or newer, JDK 17 is bundled).
2. File -> Open -> select the `CalculatorVault` folder. Let Gradle sync (needs internet once).
3. Build -> Build Bundle(s) / APK(s) -> Build APK(s).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`

## Build option B: GitHub Actions (no Android Studio)
1. Create a free GitHub account and a new repository (private is fine).
2. Unzip this project. On the repository page choose **Add file -> Upload files** and drag in everything
   inside the `CalculatorVault` folder (app, gradle, build.gradle, settings.gradle, gradle.properties, ...).
3. The hidden `.github/workflows/build.yml` file may be skipped by the web uploader. If so, choose
   **Add file -> Create new file**, type the name `.github/workflows/build.yml`, and paste the content of that file.
   (Using `git push` from a computer avoids this problem.)
4. Open the **Actions** tab -> "Build debug APK" -> **Run workflow** (it also runs on every push).
5. When it is green, open the run and download the artifact **calculator-vault-debug-apk** (a ZIP containing `app-debug.apk`).

## Install on Huawei Y7 2019
1. Copy `app-debug.apk` to the phone (USB, Bluetooth, or download it directly).
2. Open it with the Files app. When asked, allow installing from this source
   (Settings -> Security & privacy -> More settings -> Install apps from external sources / "Install unknown apps").
3. If EMUI shows a security scan or Pure Mode warning, choose to continue or turn Pure Mode off temporarily
   (Settings -> Security & privacy -> Pure mode).
4. Alternative with a PC: enable Developer options + USB debugging, then `adb install -r app-debug.apk`.
5. The app appears as **Calculator**.

## Limitations (Android 9 / EMUI)
* A normal app cannot hide or remove other apps from the Huawei Home Screen or App Drawer. The app list is a private reference list only.
* Imported files are COPIED. Originals are removed only if you confirm and the storage provider allows it.
* Restore writes to a folder you choose (Android does not allow silent writes to public folders).
* If you forget the PIN, or uninstall / clear data, the vault is gone. There is no recovery or cloud backup.
* Not verified on real hardware by the author of this code. Please test on the device and report Keystore or picker problems.
