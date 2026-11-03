# Releasing AlifUp

## One-time: create your signing key

Android only installs updates signed with the **same key** as the first version. Create it
once, keep it forever, and back it up (a USB stick and a cloud drive). If it is lost, people
must uninstall the app — losing their progress — to get a new version.

In PowerShell, in the project folder (`keytool` comes with the JDK):

```powershell
keytool -genkeypair -v -keystore alifup-release.jks -alias alifup -keyalg RSA -keysize 4096 -validity 36500
```

It asks for a password and a name; the name is shown to nobody but must not be empty.

Then copy `keystore.properties.example` to `keystore.properties` and put the password in it
(both `storePassword` and `keyPassword`, unless you chose two different ones).
Both files are ignored by git, so they never reach GitHub.

## Every release

1. Raise the version in `composeApp/build.gradle.kts`: `versionCode` by one (Android uses
   this to know it's newer), `versionName` and `packageVersion` to the new number.
2. Make sure the development switches are off: `UNLOCK_ALL` in `data/Course.kt` and
   `DEV_UNLIMITED_HEARTS` in `engine/Progress.kt`.
3. Build:

   ```powershell
   .\gradlew.bat :composeApp:assembleRelease
   ```

   The signed APK is `composeApp/build/outputs/apk/release/composeApp-release.apk`.
   Rename it to e.g. `AlifUp-1.0.0.apk`.

4. Windows installer (needs the free [WiX Toolset v3](https://github.com/wixtoolset/wix3/releases)):

   ```powershell
   .\gradlew.bat :composeApp:packageMsi
   ```

   The installer is in `composeApp/build/compose/binaries/main/msi/`.

5. On GitHub: Releases → Draft a new release → tag `v1.0.0` → attach the APK and the MSI →
   paste the release notes → Publish.

## Installing the APK on a phone

Send people the link to the release page. On the phone:

1. Open the link, download `AlifUp-x.y.z.apk`.
2. Tap the downloaded file. Android asks to allow installs from this source (the browser or
   Files app): allow it, go back, tap Install.
3. Updates: download the new APK and install it over the old one — progress is kept, as long
   as it was signed with the same key.
