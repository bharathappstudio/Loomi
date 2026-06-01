### *Real-time Messaging. Reinvented.*

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2025.01-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-34.13.0-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com)
[![WebRTC](https://img.shields.io/badge/WebRTC-Audio_Calls-FF6B6B?style=for-the-badge&logo=webrtc&logoColor=white)](https://webrtc.org)
[![License](https://img.shields.io/badge/License-MIT-A8FF78?style=for-the-badge)](LICENSE)
[![API](https://img.shields.io/badge/Min_SDK-24-orange?style=for-the-badge)](https://android-arsenal.com/api?level=24)
[![Target SDK](https://img.shields.io/badge/Target_SDK-36-blueviolet?style=for-the-badge)](https://developer.android.com)

<br/>

> **Loomi** is a feature-rich, Snapchat-inspired Android messenger built with cutting-edge Jetpack Compose and Firebase — featuring real-time chats, WebRTC audio calls, Stories with music, end-to-end encryption, and silky-smooth animations.

<br/>

## 🛠️ Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| 🎨 **UI Framework** | Jetpack Compose + Material3 | BOM 2025.01.00 |
| 🧠 **Language** | Kotlin | 2.2.10 |
| 🔥 **Backend / Auth** | Firebase Auth + Realtime DB | BOM 34.13.0 |
| 📨 **Push Notifications** | Firebase Cloud Messaging (FCM) | BOM 34.13.0 |
| 📁 **File Storage** | Firebase Storage | BOM 34.13.0 |
| 📞 **Audio Calling** | Stream WebRTC Android | 1.3.10 |
| 📷 **Camera** | CameraX | 1.4.1 |
| 🖼️ **Image Loading** | Coil Compose | 2.7.0 |
| 🔄 **Background Tasks** | WorkManager | 2.10.0 |
| 🔑 **Authentication** | Google Sign-In (Play Services) | 21.3.0 |
| 💦 **Splash Screen** | AndroidX Core Splashscreen | 1.0.1 |
| 📍 **Location** | Play Services Location | 21.3.0 |

---

## 🏗️ Project Architecture

```
com.echo.loomi/
│
├── 📱 Activities
│   ├── MainActivity.kt          ← Home feed, stories, user list
│   ├── MessageActivity.kt       ← 1:1 chat screen
│   ├── CallActivity.kt          ← WebRTC audio call screen
│   ├── LoginActivity.kt         ← Google Sign-In + permissions
│   ├── WelcomeActivity.kt       ← Profile setup (Memoji / custom photo)
│   ├── Setting.kt               ← App settings
│   └── EchoActivity.kt          ← In-app update mechanism
│
├── 🔧 Core Utilities
│   ├── RTCManager.kt            ← WebRTC peer connection manager
│   ├── EncryptionUtils.kt       ← Message encryption / decryption
│   ├── NotificationHelper.kt    ← Channels, styles, direct reply
│   ├── GoogleAuthClient.kt      ← Google auth wrapper
│   ├── BackgroundUtils.kt       ← Battery optimization helpers
│   └── ProximitySensorManager.kt ← Ear proximity for calls
│
├── 🔔 Background Services
│   ├── MessageListenerService.kt  ← Foreground: real-time listener
│   ├── LoomiFirebaseMessagingService.kt ← FCM receiver
│   ├── KeepAliveWorker.kt          ← WorkManager: service watcher
│   ├── BootReceiver.kt             ← Auto-start on device boot
│   └── DirectReplyReceiver.kt      ← Notification inline reply
│
├── 📐 Data Models
│   ├── ChatMessage.kt           ← id, senderId, receiverId, message, timestamp
│   ├── Call.kt                  ← CallData, CallState enum
│   └── user.kt                  ← SnapUser, Story
│
└── 🎨 UI Theme
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 🚀 Getting Started

### Prerequisites

```
✅ Android Studio Ladybug or newer
✅ JDK 11+
✅ Android device / emulator (API 24+)
✅ Google Firebase project
```

### 1️⃣ Clone the Repository

```bash
git clone https://github.com/bharathappstudio/Loomi.git
cd Loomi
```

Set your Firebase Realtime Database rules:

```json
{
  "rules": {
    "users": { ".read": "auth != null", ".write": "auth != null" },
    "chats": { ".read": "auth != null", ".write": "auth != null" },
    "calls": { ".read": "auth != null", ".write": "auth != null" },
    "stories": { ".read": "auth != null", ".write": "auth != null" }
  }
}
```

### 3️⃣ Configure Signing

Create `local.properties` (already git-ignored):

```properties
# local.properties  ← never commit this file!
KEYSTORE_PATH=/path/to/your/loomi.jks
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=your_alias
KEY_PASSWORD=your_key_password
```

Update `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(localProperties["KEYSTORE_PATH"] as String)
        storePassword = localProperties["KEYSTORE_PASSWORD"] as String
        keyAlias = localProperties["KEY_ALIAS"] as String
        keyPassword = localProperties["KEY_PASSWORD"] as String
    }
}
```

### 4️⃣ Build & Run

```bash
./gradlew assembleDebug        # Debug build
./gradlew assembleRelease      # Release build
./gradlew installDebug         # Install on connected device
```

---

## 🔐 Security Architecture

```
User A Device                     Firebase RTDB                   User B Device
     │                                  │                               │
     │  encrypt("Hello!")               │                               │
     │  ──── e2e:BASE64 ──────────────► │ ──── e2e:BASE64 ────────────► │
     │                                  │                    decrypt()  │
     │                                  │                    "Hello!"   │
```

### Encryption Spec

| Property | Value |
|----------|-------|
| Algorithm | XOR cipher |
| Key | Per-build static key |
| Prefix | `e2e:` on all encrypted payloads |
| Encoding | Base64 (Android Base64.DEFAULT) |
| Applied to | All chat messages + caller identity in signaling |

> ⚠️ **Note:** Current XOR cipher is a placeholder. For production, replace with **Signal Protocol** or **ECDH + AES-GCM** key exchange.

---

## 📡 WebRTC Call Flow

```
Caller                    Firebase (Signaling)              Callee
  │                              │                             │
  │── startCall() ──────────────►│                             │
  │   writes: status="ringing"   │──── FCM push ─────────────►│
  │                              │                             │
  │                              │◄── status="accepted" ───────│
  │── createOffer() ────────────►│                             │
  │   writes: sdp + type=offer   │──── onDataChange ──────────►│
  │                              │          createAnswer()      │
  │◄─────────────────────────────│◄─── sdp + type=answer ──────│
  │   setRemoteDescription()     │                             │
  │                              │                             │
  │◄══════ ICE Candidates ══════►│◄══════════════════════════►│
  │                                                             │
  │◄══════════════ P2P Audio Channel (WebRTC) ════════════════►│
```

---

## 📲 Notification System

| Channel | Importance | Style | Features |
|---------|-----------|-------|---------|
| `loomi_messages` | HIGH | `MessagingStyle` | Direct reply, avatar, deep-link |
| `loomi_calls` | HIGH | `FullScreenIntent` | Full-screen incoming call |
| `loomi_system_sync` | MIN | Silent / secret | Background service keepalive |

---


## 📄 License

```
MIT License — Copyright (c) 2025 Bharath App Studio

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files...
```

---

<div align="center">

### Built with ❤️ by [Bharath App Studio](https://bharathappstudio.github.io)

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com)
[![WebRTC](https://img.shields.io/badge/WebRTC-FF6B6B?style=flat-square)](https://webrtc.org)

*Specializing in Kotlin Multiplatform · Android · KMP · Figma/UX*

</div>
