# Mingle Booth Kala Kini — Android Photobooth

A professional, high-performance, tablet-optimized Android photobooth application built with **Jetpack Compose**, **Kotlin Coroutines**, and custom **Pure-Kotlin USB Host drivers** for Sony Alpha mirrorless cameras and HDMI video capture cards.

---

## 🌟 Key Highlights & Architecture

- **100% Native Jetpack Compose**: Modern, retro-editorial UI (warm cream, espresso, terracotta) with smooth transitions, interactive framing overlays, animated shutter flash, and analog strip previews.
- **Direct Sony PC Remote Driver (`SONY_PC_REMOTE`)**:
  - Pure-Kotlin PTP (ISO 15740 / ISO 12232) USB Host implementation (`android.hardware.usb`) with zero NDK or external native dependencies.
  - Compatible with Sony a6700, a6400, and other modern Sony Alpha cameras.
  - Supports **1-cable direct USB-C/Micro-USB OTG** connection: live preview stream + camera body physical shutter release + external studio flash synchronization + 24MP+ full-resolution photo download.
- **Native UVC HDMI Video Capture Driver (`EXTERNAL`)**:
  - Custom Pure-Kotlin USB Video Class (UVC 1.1 / 1.5) Bulk-IN streaming driver for USB HDMI capture cards (MacroSilicon MS2109, MS2130, Zasprec, Elgato Cam Link, etc.).
  - Automatic `dwMaxPayloadTransferSize` negotiation (eliminates header misalignment and JPEG stream corruption).
  - Smooth 1080p @ 30 FPS MJPEG decoding directly into Compose canvas.
  - Perfect for older Sony cameras like the **Sony a6000** or any camera with clean HDMI output.
- **Robust Shutter & Auto-Trigger Modes**:
  - Physical camera body shutter button detection (`checkNewPhotoAvailable`).
  - Bluetooth remotes / external wireless clickers / keyboard keys (`Space`, `Enter`, `Volume`).
  - On-screen touch pill trigger with configurable countdown.
- **Complete Photobooth Workflow**:
  - Interactive Screensaver / Attract Screen.
  - Multi-pose Capture with live countdown & slot aspect ratio guide overlay.
  - Review Screen: Side-by-side pose review, photostrip preview, and **single-pose Retake** without redoing all shots.
  - High-res Image Processing: Slot matrix cropping, strip templating, branded watermark overlay, animated GIF generation.
  - Results & Delivery: Real-time QR code generation for instant guest download (Cloudinary + Supabase), wireless printing, and session analytics.
- **PIN-Protected Studio Admin Dashboard**:
  - Event management, frame template selection with dynamic multi-slot configuration.
  - Real-time camera device switching (`FRONT`, `BACK`, `SONY_PC_REMOTE`, `EXTERNAL`).
  - Live video preview diagnostic test feed, image mirroring toggles, and session history.

---

## 📋 System Requirements

| Component | Minimum | Recommended |
| :--- | :--- | :--- |
| **Android OS** | Android 7.0 (API 24) | Android 14 / 15 / 16 (API 34–37) |
| **Device Form Factor** | Android Tablet (10"+) or Phone | Xiaomi Pad 6, Samsung Galaxy Tab S9, etc. |
| **USB Support** | USB Host / OTG Support | USB-C 3.2 Gen 1 (5 Gbps+) |
| **Camera** | Built-in Camera | Sony a6700, a6400, or a6000 |

---

## 🔌 Hardware & Connection Guide

### 1. Sony a6700 & Sony a6400 (Pure 1-Cable USB Mode — Recommended)
Both cameras support Sony's *"PC + Camera"* mode, allowing a single USB cable to handle live preview, physical shutter button triggers, external flash firing, and full-resolution photo transfer.

```
[ Sony a6700 / a6400 ] ──── USB-C to USB-C (or Micro-USB OTG) ────► [ Android Tablet ]
```

#### Camera Menu Settings:
* **Sony a6700**:
  - **Menu** ➔ **Network** ➔ **Cnct./PC Remote** ➔ **PC Remote Function**:
    - `PC Remote`: **On**
    - `PC Remote Connection Method`: **USB**
  - In `PC Remote Settings`:
    - `Still Img. Save Dest.`: **PC + Camera** *(Crucial: allows body shutter button to fire and saves backup to SD card)*.
* **Sony a6400**:
  - **Menu** ➔ **Network 1** ➔ `Ctrl w/ Smartphone`: **Off**.
  - **Menu** ➔ **Network 1** ➔ **PC Remote Function**:
    - `PC Remote`: **On**
    - `PC Remote Cnct Method`: **USB**
    - `Still Img. Save Dest.`: **PC + Camera**.
* **App Setting**:
  - Studio Admin ➔ Camera Facing: **`SONY_PC_REMOTE`**.

---

### 2. Sony a6000 (HDMI Capture Card Mode)
The older Sony a6000 firmware locks the camera body's shutter button when USB PC Remote is active. Therefore, the a6000 connects via HDMI capture for a rock-solid, zero-latency 1080p feed:

```
[ Sony a6000 ] ──── Micro-HDMI Cable ────► [ USB HDMI Capture Card ] ────► [ Android Tablet ]
```

#### Camera Menu Settings:
* **Menu** ➔ **Setup 🔧** ➔ `HDMI Settings` ➔ `HDMI Info. Display`: **Off** *(Delivers a clean video feed without camera icons)*.
* **Menu** ➔ **Setup 🔧** ➔ `USB Connection`: **PC Remote** (if using secondary shutter cable) or **Auto**.
* **App Setting**:
  - Studio Admin ➔ Camera Facing: **`EXTERNAL`**.
* **Capturing**:
  - Tap on-screen trigger / let countdown expire ➔ App grabs instant 1080p frame from live feed.
  - Or use a wireless Sony IR clicker / foot switch on the Multi-Terminal port.

---

## 🛠️ Project Structure

```
com.example.mingleboothkalakini/
├── MainActivity.kt               # Entry activity, USB attachment receivers, runtime permission flow
├── camera/
│   ├── SonyCameraController.kt  # High-level Sony USB PTP controller & event dispatcher
│   ├── SonyCameraSession.kt     # PTP protocol engine, extended device info, SDIO handshake, download
│   ├── PtpUsbTransport.kt       # Zero-indexed bulk transfer transport with packet boundary preservation
│   ├── HdmiCaptureController.kt # High-level UVC HDMI capture singleton controller
│   └── HdmiCaptureSession.kt    # Native Android UVC streaming engine with payload auto-negotiation
├── data/
│   ├── Models.kt                # Data models (BoothSettings, FrameItem, Slot, EventItem, PhotoSessionItem)
│   └── RemoteRepository.kt      # Cloudinary upload service, Supabase DB & REST client
├── ui/
│   ├── BoothScreens.kt          # Complete photobooth step flow (Capture, Review, Result, Retake)
│   ├── AdminScreens.kt          # PIN-protected Studio Admin settings, event editor, test feed
│   ├── MingleViewModel.kt       # Unified photobooth state machine & coroutine dispatcher
│   ├── SonyCameraUi.kt          # Sony camera status card & live preview composables
│   └── HdmiCameraUi.kt          # HDMI capture status card & live preview composables
└── util/
    └── BoothUtils.kt            # Image compositing, slot cropping, print rendering, audio FX, USB helpers
```

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- Android SDK 35+ (Compile SDK 37)
- JDK 17 or JDK 21

### Command Line
```bash
# Clone the repository
git clone https://github.com/your-org/MingleBoothAndroid.git
cd MingleBoothAndroid

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device/tablet
./gradlew :app:installDebug
```

---

## 🔒 Permissions & Security

- `android.permission.CAMERA`: Required for built-in cameras, and **mandatory on Android 14+ (API 34+)** for accessing USB Video Class (UVC) devices via Android's `UsbUserPermissionManager`.
- `android.permission.INTERNET`: For Cloudinary image uploads and Supabase event sync.
- `android.hardware.usb.host`: For direct USB communication with Sony cameras and HDMI capture cards.

---

## 📄 License
Internal proprietary software for **Mingle Booth by Kala Kini**. All rights reserved.
