<div align="center">
  <img src="docs/flow-icon.svg" width="128" height="128" alt="Flow icon">
  <h1>Flow</h1>
  <p><strong>Your videos. Your music. To go.</strong></p>
  <p>A YouTube video and audio downloader for Android.</p>
  <p><strong>English</strong> &nbsp;|&nbsp; <a href="README.ru.md">Русский</a></p>
  <p><a href="https://github.com/yessensiv/flow-downloader/releases/latest/download/Flow.apk"><img src="https://img.shields.io/badge/Download_APK-Android-C2FF70?style=for-the-badge&amp;logo=android&amp;logoColor=0F1410&amp;labelColor=0F1410" alt="Download APK for Android"></a></p>
  <p><a href="https://github.com/yessensiv/flow-downloader/releases/latest">Release notes</a> · Android 8.0+ · Debug build</p>
  <p>Android 8.0+ · Kotlin · English & Russian · No server required</p>
  <p><a href="#get-started">Get started</a> · <a href="#features">Features</a> · <a href="#build-the-android-app">Build</a> · <a href="#local-web-version">Web version</a></p>
</div>

---

Flow prepares video and audio directly on your phone and keeps your saved files in one place. Paste a YouTube link, choose the available quality or audio format, and save the result.

**Android is the main app.** This repository also includes an experimental local web version that runs on a computer.

## Features

| | What you can do |
| --- | --- |
| Video | Choose available quality and frame rate, up to 2160p when offered by the source. |
| Audio | Keep the source audio or choose MP3, M4A, Opus, FLAC or WAV; select 64–320 kbps for MP3/M4A/Opus. |
| File details | Optional embedded cover art and metadata; MKV/MP4 video containers. Cover support depends on the format. |
| Progress | See percentage, speed and estimated time remaining when available; cancel from the app or notification. |
| Background downloads | Continue preparing a file while using another app, with a foreground service notification. |
| My downloads | Browse separate video and music tabs with thumbnails and title search. |
| Multiple selection | Hold a file to select it, then tap more files to share or delete them together. |
| Phone storage | Save to system folders, open files in another app, or share them. |
| Languages | Switch the app interface between English and Russian. |

## Get started

The current project version is **0.20.0**. On your phone, tap **[Download APK](https://github.com/yessensiv/flow-downloader/releases/latest/download/Flow.apk)** for the latest published build and open the downloaded file. Allow installation from your browser if Android asks. This is a debug build; no computer or build tools are needed to install it.

1. Download the APK using the button above, or build it using [Android Studio or the Windows scripts](#build-the-android-app).
2. Copy the APK to your phone and open it. Allow installation from that source if Android asks, or install through USB as described below.
3. Open Flow and paste a link, or use **YouTube → Share → Flow**.
4. Choose **Video** or **Audio**, tap **Show options**, then choose a quality or format.
5. Start downloading. Android 10+ saves the finished file automatically to Flow's system folder, including in the background. Android 8–9 opens the system save picker. Find saved files in **My downloads**.

**Selecting several files:** hold any saved item, then tap the others. Use the group actions to share or delete. Cancel selection or remove the last checkmark to return to normal browsing. Deleting a file from the device requires confirmation.

### Where are my files?

| Device | Save location |
| --- | --- |
| Android 10 and newer | Videos: `Movies/Flow` · Music: `Music/Flow` |
| Android 8–9 | Choose a location with the system file picker. |

Saved files stay on your phone until you delete them. The Android app does not need the local web server.

## Build the Android app

Requirements: **JDK 17**, **Android SDK Platform 35**, and **Build Tools 35.0.0**. Supported device architectures: ARM64, ARMv7 and x86_64.

### Android Studio

1. Clone this repository and open the `android/` folder in Android Studio.
2. Install SDK Platform 35 and Build Tools 35.0.0 through SDK Manager.
3. Sync the project and build the debug APK.

Output, relative to the repository root:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Windows terminal

Run these commands from the repository root. The setup script downloads and verifies the tools into the ignored `.tools/android` directory.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1
$sdk = (Resolve-Path .tools\android\sdk).Path
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdk" --licenses
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
.\scripts\build-android.ps1
```

For macOS/Linux, configure JDK 17 and the Android SDK, then run `./gradlew assembleDebug` from `android/`.

### Install over USB

Enable USB debugging on your phone, connect it and accept the computer's authorization prompt. On Windows, from the repository root:

```powershell
$env:ANDROID_USER_HOME = "$PWD\.tools\android\user-home"
& .\.tools\android\sdk\platform-tools\adb.exe install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

Use the same command to update an installed build signed with the same key. These instructions create a development build, not a Google Play release.

## Good to know

- **Quality depends on the source.** Flow does not upscale video. Converting to MP3 at 192 kbps does not improve the original audio quality.
- **Choose MKV or MP4 for video.** Playback support depends on the player and source codecs; changing the container does not change the video codec.
- **One file is processed at a time.** Downloads depend on YouTube availability and your connection. Private videos, live streams and content requiring sign-in may not work.
- **Background work has limits.** Android battery restrictions or force-stopping Flow can interrupt a download. An active task does not resume after a reboot or force stop.
- **YouTube changes can affect downloads.** Try the app's engine update action when extraction fails. Updates cannot guarantee that every link will work.
- **No YouTube credentials are required.** Flow does not use YouTube cookies or account passwords.

## Local web version

The web app is an experimental local prototype. It uses a Node.js server with yt-dlp and FFmpeg to prepare files.

Requirements: **Node.js 24+**, npm, FFmpeg and internet access.

```sh
npm ci
npm run setup:media
```

On Windows, install the local FFmpeg tools:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-ffmpeg.ps1
```

On macOS/Linux, install FFmpeg with your system's package manager. Then start the app:

```sh
npm run dev
```

Open [localhost:3000](http://127.0.0.1:3000). For custom executable locations, copy `.env.example` to `.env.local` and set `YTDLP_PATH` and/or `FFMPEG_PATH`.

Unlike Android, the web version keeps prepared files on its server for **5 minutes**. Jobs and quotas live in a single process and are lost on restart. Public hosting is not configured; serverless or multi-instance deployment needs additional storage and job management.

## Development

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start the local web development server. |
| `npm run build` | Build the web app. |
| `npm run start` | Start the built web app. |
| `npm run typecheck` | Check TypeScript types. |
| `npm test` | Run web tests. |
| `.\scripts\build-android.ps1` | Build the Android debug APK on Windows. |

```text
android/   Native Kotlin app
app/       Web interface and API
lib/       Web server logic, formats and download jobs
tests/     Web tests
scripts/   Tool setup and local checks
docs/      README assets
```

Found a bug? [Open an issue](https://github.com/yessensiv/flow-downloader/issues) with your app version, Android version, steps to reproduce, and the error text or a screenshot.

## Credits

Built with [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [FFmpeg](https://ffmpeg.org/). The web app uses [Next.js](https://nextjs.org/). Dependencies have their own licenses and distribution terms.

Save content you have permission to download. Flow is an independent project and is not affiliated with YouTube or Google.
