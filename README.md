# LootBox (Android)

Kotlin + XML (ViewBinding). A download manager (direct links + YouTube / Facebook / Instagram via yt-dlp): paste a link, download with
pause / resume / cancel, see finished files in a library, change a few settings.

## Screens
- Home: logo, URL box with Paste button, Download, latest download card
- Downloads: active (progress, speed, pause/resume, cancel), completed, failed (Retry)
- Library: grid of finished files (thumbnail + length), Open / Share / Rename / Delete
- Settings: video quality, simultaneous downloads, Wi-Fi only, notifications, dark theme

## YouTube / Facebook / Instagram
Links from these sites go through yt-dlp (library `youtubedl-android`, same engine as Seal).
Other web-page links are also tried with yt-dlp. Direct file links use the plain HTTP downloader.
Private / login-only videos cannot be downloaded. The APK gets about 30-50 MB bigger (Python + FFmpeg).

## Folder structure
```
build.gradle.kts, settings.gradle.kts, gradle.properties
.github/workflows/build-apk.yml      GitHub Actions: builds the debug APK
app/build.gradle.kts                 module config + dependencies
app/src/main/AndroidManifest.xml
app/src/main/java/com/siraanabin/lootbox/
  MainActivity.kt                    bottom navigation + share-intent handling
  HomeFragment.kt  DownloadsFragment.kt  LibraryFragment.kt  SettingsFragment.kt
  DownloadService.kt                 foreground service: HTTP download, resume, save to Downloads/LootBox
  DownloadStore.kt  Model.kt         download list (saved in SharedPreferences)
  DownloadsAdapter.kt  LibraryAdapter.kt  Thumbs.kt  Util.kt  Prefs.kt  LootBoxApp.kt
app/src/main/res/                    layouts, colors (light + night), icons, logo
```

## Build
- GitHub: upload/push the project contents to the `main` branch. GitHub Actions builds a signed APK and publishes it as a Release.
- Android Studio: open this folder (File > Open), let Gradle sync, press Run.
  (No gradle wrapper is included; Android Studio can create one, or use `gradle assembleDebug`.)

## Notes
- minSdk 29 (Android 10+). Files are saved to Downloads/LootBox with MediaStore, no storage permission needed.
- Only direct file links work. YouTube page links are rejected on purpose.
- Share a link from a browser to LootBox and it fills the URL box.
- Change `applicationId` in app/build.gradle.kts if you need your old package name.


## Updates (friends get them automatically)
- Every push to `main` builds the APK and publishes it as a GitHub Release (`build-N`).
- The version number is the Actions run number, so no manual version bump is needed.
- The app checks the latest Release on start (and in Settings > App update) and offers to install it.
- The APK is signed with your own private key stored in GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`),
  so a new APK installs over the old one. Configure the four repository secrets before the first build: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Never commit the keystore file. Keep a backup copy: if it is lost, updates cannot be installed. The workflow uses version codes starting above 10000 to ensure the new build is newer than older 1xxx builds.
- Settings > Update download engine updates yt-dlp without a new APK. Use it when YouTube stops working.
- The repo must stay public for the in-app update check.

## Other features
- Quality choice before each YouTube / Facebook / Instagram download (default in Settings).
- Optional playlist download, cookies.txt import for private videos, auto retry on network errors.
