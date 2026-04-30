<div align="center">

<img src="void-radio.png" alt="VoidRadio logo" width="120">

# 📻 void-radioplayer

A small Android radio player built with Kotlin.  
Made for live radio streaming without the usual bloated app nonsense.

![Android](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)
![Status](https://img.shields.io/badge/status-in%20development-orange?style=for-the-badge)

</div>

---

## ✨ What is this?

**void-radioplayer** is a lightweight Android radio streaming app.

It plays live radio streams, supports custom stations, and can import `.m3u` playlists.  
Basically: radio app, but without the usual mobile-app slop.

This is mostly a personal project / learning project, but it already runs on real Android devices.

---

## 📥 Downloads

APK builds are hosted on the voidcore CDN:

```text
https://cdn.voidcore.dev/apps/void-radioplayer/
```

GitHub Releases exist, but builds are not uploaded there right now because I am too lazy to maintain both.

The CDN is currently the main place for downloadable APKs.

> This app is not on the Play Store.  
> You may need to allow installing apps from unknown sources on your Android device.

---

## 🚀 Features

- 📻 Live radio playback
- 🎧 Direct radio stream support
- 📄 `.m3u` playlist import
- ➕ Add custom radio stations
- 🗑️ Delete stations inside the app
- 🗂️ Station categories:
  - English
  - German
  - Hungarian
  - Custom
- 🔁 Basic reconnect handling
- 📶 Network / stream error indicator
- 🔔 Foreground playback notification
- 🎛️ Android Media3 / ExoPlayer based playback
- 🖤 Dark voidcore-style UI

---

## 📡 Included Stations

The app currently ships with a few default stations.

### English

- BBC Radio 1
- BBC Radio 6 Music
- KEXP 90.3

### German

- Ö3
- Energy Wien
- Kronehit

### Hungarian

- Retro Radio
- Sláger FM
- Rádió 1

You can add your own stations inside the app.

---

## 🖼️ Screenshots

Screenshots coming soon.

<!--
Example:

<img src="docs/screenshots/main.png" width="250">
-->

---

## 🛠️ Tech Stack

- **Kotlin**
- **Android SDK**
- **Gradle**
- **AndroidX**
- **Media3 / ExoPlayer**

---

## 📱 Requirements

### To install the APK

- Android 8.0 or newer

The app uses:

```text
minSdk 26
targetSdk 36
```

### To build from source

- IntelliJ IDEA or Android Studio
- Android SDK
- JDK 11+
- Gradle wrapper included in the repo

---

## 🧑‍💻 Build from Source

Clone the repository:

```bash
git clone https://github.com/Kyoo123/void-radioplayer.git
cd void-radioplayer
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK will be created under:

```text
app/build/outputs/apk/debug/
```

---

## ⚙️ Configuration

The project uses a Gradle build config value for the primary fallback stream URL:

```kotlin
BuildConfig.PRIMARY_STREAM_URL
```

It is configured through `gradle.properties`:

```properties
RETROPLAYER_PRIMARY_STREAM_URL=https://example.com/stream.mp3
```

Replace the URL with whatever default stream you want.

---

## ➕ Adding Stations

Stations use this structure:

```kotlin
Station(
    name = "Station Name",
    url = "https://example.com/stream.mp3",
    category = "custom"
)
```

Supported categories:

```text
english
german
hungarian
custom
```

The app can also import `.m3u` playlists and extract station names / stream URLs automatically where possible.

---

## 📄 M3U Import

You can paste a `.m3u` playlist URL into the station manager.

The app will try to:

1. Download the playlist
2. Read the station names
3. Extract stream URLs
4. Add them to the selected category

Useful for internet radio playlists and station bundles.

---

## 📦 Why APKs are not stored in Git

honestly? because im lazy

Instead, builds are hosted on the voidcore CDN:

[voidcore cdn](https://cdn.voidcore.dev/apps/void-radioplayer/)

Clean repo, easy downloads, less suffering.

---

## 🗺️ Roadmap

Things that might be added later:

- ⭐ Favorite stations
- 🔍 Station search
- 💾 Remember last selected station
- 🎚️ Volume controls
- 🧪 Better stream validation
- 📲 Better notification controls
- 🎨 More UI polish
- 📸 Screenshots in README
- 🏷️ Versioned releases
- 🤖 Maybe GitHub Actions builds, if motivation appears

---

## 🤝 Contributing

This is mainly a personal project, but feel free to:

- fork it
- open issues
- suggest improvements
- break it and fix it again

Good ideas are welcome.

Bad ideas are also welcome, but they may be laughed at first.

---

## ⚠️ Disclaimer

This app only plays publicly available radio stream URLs.

Radio station names, streams, and logos belong to their respective owners.  
This project is not affiliated with any of the listed radio stations.

---

## 📄 License

This project is licensed under the MIT License.

See [`LICENSE`](LICENSE) for details.

---

## 👤 Author

Built by **Kyo / voidcore**

- GitHub: `Kyoo123`
- CDN builds: `cdn.voidcore.dev`

---

<div align="center">

made with questionable decisions and working code™

</div>