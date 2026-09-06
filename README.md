# LocalShelf

LocalShelf is a serverless Android media library for movies and TV episodes stored on your phone or on storage providers exposed through Android's Storage Access Framework.

## What it does

- No Jellyfin/Plex server and no account required.
- Pick one or more folders and keep persistent read access to them.
- Movies and TV libraries with search and sorting.
- TV detection for `S01E02`, `1x02`, and `Season 1 Episode 2` naming.
- On-device video thumbnails.
- Continue Watching / Recently Added / Favorites.
- Watched and unwatched state.
- Movie details and TV season/episode views.
- Media3/ExoPlayer playback with saved resume position.
- Detects matching `.srt`, `.vtt`, `.ass`, `.ssa`, and `.ttml` subtitle files in the same folder.
- No broad storage permission; the user chooses exactly which folders the app can read.

## Open and build in Android Studio

1. Install a current Android Studio release with Android SDK 37 available.
2. Open this folder as a project.
3. Let Android Studio sync dependencies.
4. Run the `app` configuration on your phone, or use **Build > Build APK(s)**.

The project uses Android Gradle Plugin 9.4.0, Gradle 9.6, JDK 17, compile/target SDK 37, and Media3 1.11.0.

## Build an APK with GitHub Actions

This repository includes `.github/workflows/build-apk.yml`. Push the project to a GitHub repository and run **Build Android APK** under the Actions tab. The workflow installs the Android SDK and Gradle, builds `app-debug.apk`, and publishes it as the `LocalShelf-debug-apk` workflow artifact.

## Filename examples

Movies:
- `Dune Part Two (2024).mkv`
- `Blade.Runner.2049.2017.2160p.mkv`

Shows:
- `The Expanse S02E05 Home.mp4`
- `The Expanse 2x06 Paradigm Shift.mkv`

Subtitles should normally share the video filename stem, for example:
- `Dune Part Two (2024).mkv`
- `Dune Part Two (2024).en.srt`

## Privacy

LocalShelf does not require an Internet permission and does not upload your media library. Media paths, favorites, watched state, and playback progress are stored locally in the app's SharedPreferences.
