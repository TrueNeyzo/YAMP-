# YAMP Android — APK Build 1

This repository wraps YAMP Build 28.2 in a minimal Android WebView application.

## Build the APK on GitHub

1. Create a new GitHub repository.
2. Upload **the contents of this folder** to the repository root.
3. Open **Actions** → **Build YAMP APK**.
4. Press **Run workflow**.
5. When the job finishes, open it and download the artifact **YAMP-APK-Build-1**.
6. Unzip the artifact and install `YAMP-APK-Build-1.apk` on Android.

The first build is deliberately a debug APK. No signing secrets are required.

## YAMP update

The web app is stored at:

`app/src/main/assets/index.html`

To test a newer YAMP build, replace that file and run the workflow again.

## Notes

- Internet permission is enabled.
- JavaScript and DOM/localStorage are enabled in WebView.
- Yandex OAuth navigations are opened in the system browser, matching the existing copy/paste-token workflow.
- This first wrapper does not yet implement Android MediaSession/background playback.
