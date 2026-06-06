# 🎵 MyMusicMuy (MMM) 🎵

<p align="center">
  <img src="app/src/main/res/drawable/ic_play" alt="MyMusicMuy Logo" width="120" height="120" style="border-radius: 50%;" />
</p>

<p align="center">
  <strong>A Premium, Lightweight, and Seamless Material-Designed Local Music Player for Android.</strong>
</p>

<p align="center">
  <a href="https://github.com/com.mymusic.muy/MyMusicMuy/releases/latest">
    <img src="https://img.shields.io/github/v/release/com.mymusic.muy/MyMusicMuy?style=for-the-badge&color=00E5FF&label=LATEST%20RELEASE&logo=github" alt="Latest Release" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-green?style=for-the-badge&logo=android&logoColor=white" alt="Platform Compatibility" />
  <img src="https://img.shields.io/badge/Language-Kotlin%20/%20Java-purple?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/Build%20System-Gradle%20KTS-blue?style=for-the-badge&logo=gradle&logoColor=white" alt="Build System" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/UI-Material%203%20/%20Modern-FF4081?style=for-the-badge" alt="UI Theme" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20/%20Services-orange?style=for-the-badge" alt="Architecture" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License" />
</p>

---

## ✨ Features

- 🌟 **Full Screen Player (FSP):** Highly immersive dynamic UI with a futuristic dark glassmorphism layout (`#0A0A0C`).
- 🎨 **Dynamic Color Extraction:** Powered by `Android Palette`, automatically adapting the background vibe based on the current album art.
- 📜 **Smart Lyrics Synchronizer:** Seamlessly tracks and scrolls synced lyrics utilizing custom high-precision handlers linked directly with local `.lrc` or integrated document providers.
- 🖼️ **Blur Backdrop Canvas:** High-fidelity background blur aesthetics rendering live cover arts via Glide cache engines.
- ⚡ **Asynchronous Efficiency:** Built entirely using Kotlin Coroutines (`Dispatchers.Main` / `Dispatchers.IO`) for stutter-free audio parsing and non-blocking view interactions.
- 🎛️ **Background Music Service:** Robust custom `MusicService` binder running smooth audio tracks and broadcasts independently of app life-cycle.

---

## 🛠️ Built With & Tech Stack

- **Core Core & UI:** Kotlin, Java, `AppCompat`, `ConstraintLayout`, `ViewPager2`, `Material 3`
- **Graphics & Palette:** `androidx.palette:palette-ktx`, `Glide v4`
- **Architecture & System:** Android Foreground Services, `BroadcastReceiver`, `LifecycleScope`, `KotlinX Coroutines`
- **Minimum SDK Supported:** API 26 (Android 8.0 Oreo)
- **Target SDK Compilation:** API 35 (Android 14/15 Ready)

---

## 🚀 Installation & Build Guide

You can easily set up, compile, and build the source files manually either on standard desktop setups or natively inside Android terminal sandboxes like **Termux**.

### 1️⃣ Clone the Repository
Open your preferred terminal application and execute:
```bash
git clone [https://github.com/YOUR_GITHUB_USERNAME/MyMusicMuy.git](https://github.com/YOUR_GITHUB_USERNAME/MyMusicMuy.git)
cd MyMusicMuy
