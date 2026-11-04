# SKILLS.md — Technical Skills & Task Playbooks

This document outlines the specialized operational skills, diagnostic procedures, and troubleshooting playbooks required to maintain and enhance the **Mingle Booth Kala Kini** codebase.

---

## 📸 Skill 1: Sony Alpha USB PC Remote (`SONY_PC_REMOTE`)

### 1.1 Architectural Overview
The Sony PC Remote engine communicates with Sony cameras via PTP (Picture Transfer Protocol) over Android's USB Host API without requiring external SDKs or NDK binaries.

- **Transport**: [`PtpUsbTransport.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/PtpUsbTransport.kt) (manages bulk endpoints, transaction IDs, and multi-packet buffering).
- **Session**: [`SonyCameraSession.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/SonyCameraSession.kt) (handles Sony SDIO handshakes, live view JPEG extraction, and full-res photo download).
- **Controller**: [`SonyCameraController.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/SonyCameraController.kt) (singleton managing coroutine loop, reconnection, and auto-capture event emission).

### 1.2 Camera Configuration Playbook
| Camera Model | Connection Type | Required In-Camera Settings |
| :--- | :--- | :--- |
| **Sony a6700** | USB-C to USB-C | `Menu ➔ Network ➔ Cnct./PC Remote ➔ PC Remote: On`<br>`PC Remote Cnct Method: USB`<br>`PC Remote Settings ➔ Still Img Save Dest: PC + Camera` |
| **Sony a6400** | Micro-USB OTG | `Menu ➔ Network 1 ➔ Ctrl w/ Smartphone: Off`<br>`PC Remote Function ➔ PC Remote: On`<br>`PC Remote Cnct Method: USB`<br>`Still Img. Save Dest.: PC + Camera` |

### 1.3 Troubleshooting: "Check the connected device."
- **Symptom**: Camera displays *"Check the connected device."* on its rear LCD.
- **Root Causes & Solutions**:
  1. *Wrong USB mode*: Camera is set to "Auto" or "Mass Storage". Switch to **"PC Remote"** in camera menu.
  2. *Permission denied*: Android popped up the USB permission dialog and user tapped Cancel or dismissed it. Check logcat:
     ```bash
     adb logcat -d -s MingleUSB
     ```
     Fix: Re-plug USB, check **☑ Always allow**, and tap **Allow**.
  3. *Smartphone Control Active*: On a6400, ensure `Ctrl w/ Smartphone` is disabled.

---

## 🎥 Skill 2: Native UVC HDMI Video Capture (`EXTERNAL`)

### 2.1 Architectural Overview
Provides instant, 30 FPS 1080p live preview from any camera with clean HDMI output (e.g. Sony a6000) using inexpensive USB capture cards (MS2109, MS2130, Zasprec, Cam Link).

- **Session**: [`HdmiCaptureSession.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/HdmiCaptureSession.kt)
- **Controller**: [`HdmiCaptureController.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/HdmiCaptureController.kt)
- **UI**: [`HdmiCameraUi.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/ui/HdmiCameraUi.kt)

### 2.2 Critical UVC Implementation Rules
1. **Dynamic dwMaxPayloadTransferSize**:
   - Never hardcode the bulk transfer buffer to 64KB.
   - Sizing the buffer to match the negotiated `dwMaxPayloadTransferSize` (16,384 bytes on MS2109) guarantees that every bulk read starts at byte 0 with a single 12-byte UVC packet header.
2. **Android 14+ Runtime Camera Permission**:
   - `UsbUserPermissionManager` requires `android.permission.CAMERA` before granting USB access to video class devices.
   - Always ensure `MainActivity.promptUsbPermissions()` checks and requests runtime `CAMERA` permission before requesting USB device access.
3. **Camera HDMI Settings (Sony a6000)**:
   - Set `Setup ➔ HDMI Settings ➔ HDMI Info. Display: Off` to eliminate exposure parameters, gridlines, and battery icons from the video feed.

---

## 🖼️ Skill 3: Photostrip Compositing & Print Engine

### 3.1 Template Geometry & Slot Alignment
Photostrip layouts are defined by dynamic `FrameItem` objects loaded from Supabase or local assets.
- **Slot Definition**: Each photo slot is defined by `Slot(slotIndex, x, y, w, h)` in pixels relative to the frame dimensions (e.g. 1803×795 px inside a 1200×1800 4R frame).
- **Center-Crop Algorithm** ([`BoothUtils.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/util/BoothUtils.kt)):
  ```kotlin
  // Scale and center-crop raw camera photo to fit slot aspect ratio
  val scale = maxOf(slotW.toFloat() / rawW, slotH.toFloat() / rawH)
  val targetW = (rawW * scale).toInt()
  val targetH = (rawH * scale).toInt()
  val offsetX = (targetW - slotW) / 2
  val offsetY = (targetH - slotH) / 2
  ```

### 3.2 High-Resolution Strip Rendering
- Renders the composite strip on a high-DPI `android.graphics.Canvas`.
- Draws slot photos in order, overlays the PNG frame graphic, adds branding text/date, and generates a printable bitmap.
- Simultaneously generates an animated GIF of all captured poses with a 500ms delay per frame.

---

## ☁️ Skill 4: Cloud Sync & Guest Delivery Pipeline

### 4.1 Pipeline Workflow ([`RemoteRepository.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/data/RemoteRepository.kt))
1. **Capture Complete**: Raw bitmaps composited into `stripBitmap` and `gifBytes`.
2. **Cloudinary Upload**:
   - Direct multipart HTTP POST to `https://api.cloudinary.com/v1_1/<cloud_name>/image/upload`.
   - Uploads composite strip (`photo_strip.jpg`) and animated GIF (`animation.gif`).
3. **Supabase Persistence**:
   - Inserts record into `photo_sessions` table with layout metadata, photo slot URLs, and timestamp.
4. **Guest QR Code**:
   - Encodes web viewer URL: `https://mingle-booth.vercel.app/session/<session_id>`.
   - Renders a clean 512×512 QR code bitmap using ZXing for instant guest scanning.

---

## 📱 Skill 5: Device Testing & ADB Diagnostics Playbook

### 5.1 Common Diagnostic Commands

```bash
# Check connected ADB devices
adb devices

# Stream HDMI capture logs
adb logcat -s HdmiCaptureSession

# Stream Sony PTP controller logs
adb logcat -s SonyCameraController SonyCameraSession MingleUSB

# Inspect all connected USB devices
adb shell dumpsys usb

# Simulate physical shutter trigger (Volume Down or Camera key)
adb shell input keyevent 25 # KEYCODE_VOLUME_DOWN
adb shell input keyevent 27 # KEYCODE_CAMERA

# Simulate screen tap on Start Photobooth or Shutter Pill
adb shell input tap 540 1640  # Tap Start Photobooth
adb shell input tap 540 2170  # Tap Shutter Pill
```

### 5.2 Build & Deploy Quick Commands
```bash
# Clean debug build
./gradlew :app:assembleDebug

# Deploy directly to device
./gradlew :app:installDebug
```
