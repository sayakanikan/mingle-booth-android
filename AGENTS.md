# AGENTS.md — AI Agent Engineering Handbook

Welcome to **Mingle Booth Kala Kini** Android codebase. This handbook documents core architectural invariants, hardware communication protocols, and execution guardrails that every AI coding agent MUST follow when maintaining or extending this project.

---

## 🏛️ Core Architectural Invariants

### 1. Dual-Camera Architecture & Separation of Concerns
The project has three distinct camera backends configured in `BoothSettings.cameraFacing`:
- **`"FRONT"` or `"BACK"`**: Managed by **CameraX** ([`BoothScreens.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/ui/BoothScreens.kt)).
- **`"SONY_PC_REMOTE"`**: Pure PTP over USB Host for modern Sony Alpha cameras ([`SonyCameraController.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/SonyCameraController.kt)).
- **`"EXTERNAL"`**: Native Pure-Kotlin UVC Host for HDMI capture cards ([`HdmiCaptureController.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/HdmiCaptureController.kt)).

> [!CRITICAL]
> **RULE #1: NEVER BREAK SONY PC REMOTE WHEN MODIFYING HDMI CAPTURE (OR VICE VERSA).**
> Any modification to [`BoothScreens.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/ui/BoothScreens.kt) or USB receivers must ensure that both camera pathways remain completely intact, functional, and isolated.

> [!CRITICAL]
> **RULE #2: NEVER FALL BACK TO INTERNAL CAMERAS WHEN AN EXTERNAL SOURCE IS SELECTED.**
> If the user sets `cameraFacing = "SONY_PC_REMOTE"` or `"EXTERNAL"`, the app must display the dedicated connection status card (`SonyStatusCard` or `HdmiStatusCard`). It must NEVER fall back to CameraX or built-in tablet cameras.

---

### 2. Pure-Kotlin USB Host Philosophy (No NDK / No LibUVC)
- **Constraint**: Do NOT introduce C/C++ NDK shared libraries (`.so`), `libuvc`, or external native dependencies for USB communication.
- **Reason**: The app must run seamlessly across all Android architectures (arm64-v8a, armeabi-v7a, x86_64 tablets) with standard Gradle builds without ABI mismatches or NDK toolchain breakage.
- All USB communication uses `android.hardware.usb.UsbManager`, `UsbDeviceConnection`, and `UsbEndpoint` via coroutines on `Dispatchers.IO`.

---

### 3. Android API 34+ (Android 14/15/37) UVC Security Requirement
- **Rule**: On modern Android versions, Android's `UsbUserPermissionManager` will **silently deny USB permission** for UVC Video Class devices (`class=14` or `class=239, subclass=2`) if the app does not have runtime `android.permission.CAMERA`, throwing:
  `UsbUserPermissionManager: Camera permission required for USB video class devices`.
- **Implementation**: [`MainActivity.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/MainActivity.kt) MUST register `cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission())` and request `android.permission.CAMERA` before attempting to request USB permissions for HDMI capture cards.

---

### 4. UVC Streaming Engine Invariants ([`HdmiCaptureSession.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/HdmiCaptureSession.kt))
- **Payload Transfer Size**: Always negotiate `VS_PROBE_CONTROL` and `VS_COMMIT_CONTROL` before bulk streaming. Extract `dwMaxPayloadTransferSize` (bytes 22..25) from the negotiated response.
- **Buffer Alignment**: Sizing the receive buffer to the exact `dwMaxPayloadTransferSize` (typically 16,384 bytes for MacroSilicon MS2109) guarantees that every bulk transfer contains exactly one UVC payload header at byte 0. If oversized buffers (e.g. 64KB) are used, Android will concatenate multiple transfers, embedding unstripped 12-byte headers inside the JPEG stream and causing Skia decoder errors (`found marker 0x0c instead of RST`).
- **EOF & EOI Detection**: Bulk packet byte 1 contains UVC header flags (`headerInfo and 0x02 != 0` is EOF). Fallback to JPEG End-Of-Image marker (`0xFF, 0xD9`) at `[bytesRead - 2]` and `[bytesRead - 1]` to ensure zero-latency frame dispatch even if hardware firmware delays the EOF bit.

---

### 5. Sony PTP USB Engine Invariants ([`SonyCameraSession.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/SonyCameraSession.kt), [`PtpUsbTransport.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/PtpUsbTransport.kt))
- **Shift Buffer**: PTP over USB sends command/data/response packets. [`PtpUsbTransport`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/camera/PtpUsbTransport.kt) uses an internal zero-indexed shift buffer so trailing bytes from data phases are never lost during response evaluation.
- **Protocol Version Negotiation (Mode 200 vs Mode 300)**: Sony bodies before BIONZ XR (such as `ILCE-6400`, `ILCE-6000`, `ILCE-7M3`) MUST connect with `PROTOCOL_2_00` (`0x00C8`). Sending Mode 300 (`0x012C`) causes older firmware to fail negotiation and lock the camera screen into `"Check the connected device."`. The model is resolved via `GetDeviceInfo` before SDIO handshake to choose the correct protocol on the very first attempt.
- **Still Image Save Destination (`0xD222`)**: Automatically configured to `0x0011` (`PC+Camera`), ensuring that physical shutter button presses save the captured photo to the SD card and place the full-res JPEG in `HANDLE_IMAGE (0xffffc001)`.
- **Physical Camera Trigger**: `checkNewPhotoAvailable()` polls `Ptp.OC_GET_OBJECT_INFO` on `HANDLE_IMAGE (0xffffc001)`. A new photo is signaled by filename/date/sequence/size changes relative to `baselineMeta`.
- **Auto-Capture Concurrency**: Live view polling and shutter download share a single USB pipe protected by `ioMutex`. When a trigger is detected, the controller downloads the full JPEG (`downloadPhoto()`), decodes it, and emits it to `_capturedPhotoEvent`.

---

### 6. Photobooth Concurrency & State Machine ([`BoothScreens.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/ui/BoothScreens.kt))
- **`isCapturingNow` Flag**: Must be set during capture, but **must NEVER block internal photo saving**. The helper `saveAndAdvancePhoto(rawBmp)` handles slot assignment, flash animation, sound effects, and step transition without triggering self-deadlocks.
- **Single-Pose Retake**: When `retakeSlotIndex` is non-null, only that specific pose is recaptured. Once captured, the app merges the new bitmap with existing photos and immediately advances to the Review screen without clearing other shots.

---

### 7. Pure-Kotlin High-Fidelity Animated GIF Engine ([`BoothUtils.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/util/BoothUtils.kt))
- **Adaptive 256-Color Median-Cut Quantization**: Never regress to fixed 3-3-2 color palettes. The encoder generates an adaptive palette tailored to the exact skin tones and lighting of the session.
- **Floyd-Steinberg Error Diffusion Dithering**: Eliminates harsh banding and retro "filter" artifacts on skin tones and smooth gradients.
- **15-Bit RGB Lookup Table (LUT)**: Fast $O(1)$ nearest-color lookups ($32 \times 32 \times 32$) with perceptual weighting ($2\Delta R^2 + 4\Delta G^2 + 3\Delta B^2$) ensure encoding runs in under 300ms on device.
- **Natural Aspect Ratio**: [`buildAnimatedGif`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/util/BoothUtils.kt) automatically adapts to portrait, landscape, or square orientations without forcing a 4:3 landscape crop.

---

### 8. Local Device Gallery Backup Invariant ([`BoothUtils.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/util/BoothUtils.kt), [`MingleViewModel.kt`](file:///D:/MulusProject/MingleBoothAndroid/app/src/main/java/com/example/mingleboothkalakini/ui/MingleViewModel.kt))
- **Execution Timing**: When `saveToLocalGallery` is active on the current event, the local backup (`saveSessionToLocalGallery`) MUST run **strictly before** the cloud upload process begins.
- **Complete Backup Scope**: Backs up the rendered photostrip (`.jpg`), animated GIF (`.gif`), and every individual pose capture (`.jpg`) to the Android MediaStore under `Pictures/MingleBooth/{eventTitle}/`.
- **Fault-Tolerant Execution**: Any filesystem or MediaStore exception is logged safely and must never crash or prevent the cloud upload from completing.

---

## 🛠️ Tool Usage & Development Guardrails

1. **NEVER Use Shell Commands for File Editing**:
   - `sed`, `awk`, `echo >`, `cat << EOF`, or redirection in `run_shell_command` are **strictly prohibited**.
   - **Only** use `replace_file_content` (single block), `multi_replace_file_content` (multiple blocks), or `write_file` (full file).
   - If an edit conflict occurs due to file modification, re-read the file with `read_file` and retry.

2. **Always Verify Builds After Edits**:
   - Every file change should be validated with:
     ```
     gradle_build(":app:assembleDebug")
     ```
   - Do not claim a task is completed until the build passes with `Build finished successfully`.

3. **Verify UI & Physical Device State**:
   - Use `studio_ui_state` to inspect screen hierarchies and clickable bounds.
   - Use `studio_take_screenshot` to verify visual layout, camera live feed clarity, and preview rendering.
   - Use `studio_read_logcat` with tag filters (`HdmiCaptureSession`, `SonyCameraController`, `MingleUSB`) to debug low-level hardware issues.

---

## 🎨 Design System & Theming Tokens

Always maintain the warm retro-photobooth visual language:
- `Espresso` (`#221B17`): Primary text, headings, dark icons.
- `Terracotta` (`#BD5338`): Primary accent, action buttons, active pill borders.
- `SageGreen` (`#4F6D5A`): Success badges, ready camera status indicators.
- `CardCream` (`#FFFDF8`): Backgrounds for floating cards, pills, dialogue surfaces.
- `WarmSand` (`#F7F3EB`): Screen background.
- `BorderWarm` (`#E6DFD3`): Subtle card borders.

---

## 🤝 Multi-Agent Workflow & Orchestration (Antigravity CLI)

This repository includes a 5-agent delivery team managed by an **Orchestrator** under [`.agents/skills/`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/):

| Role | Subagent / Skill ID | Swarm Handle | Core Responsibility |
| :--- | :--- | :--- | :--- |
| **Orchestrator** | [`orchestrator`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/orchestrator/SKILL.md) | `@coordinator` | Routes tasks, manages agent handoffs, and enforces end-to-end delivery. |
| **Product Manager** | [`product-manager`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/product-manager/SKILL.md) | `@pm` | Ideates best features, enforces YAGNI scope, and defines acceptance criteria (PRB). |
| **System Analyst** | [`system-analyst`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/system-analyst/SKILL.md) | `@sa` | Plans features/issues/adjustments, checks `AGENTS.md` invariants, and maps target files. |
| **UI/UX Designer** | [`ui-ux-designer`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/ui-ux-designer/SKILL.md) | `@uiux` | Designs Jetpack Compose kiosk flows, touch targets (`>= 48.dp`), and retro color tokens. |
| **Fullstack Developer** | [`fullstack-developer`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/fullstack-developer/SKILL.md) | `@dev` | Implements Kotlin/Compose/USB/Cloud code and validates `./gradlew :app:assembleDebug`. |
| **QA Engineer** | [`qa-engineer`](file:///D:/MulusProject/MingleBoothAndroid/.agents/skills/qa-engineer/SKILL.md) | `@qa` | Audits code against PRB criteria & invariants, runs build/ADB checks, and signs off or loops back to `@dev`. |

### End-to-End Handoff Chain
`User ➔ Orchestrator ➔ @pm (PRB) ➔ @sa (Tech Spec) ➔ @uiux (UI Spec) ➔ @dev (Code + Build) ⇄ @qa (Verification) ➔ Orchestrator`

- **Subagent Pipeline Mode**: The Orchestrator registers agents with `define_subagent` and invokes them sequentially/parallelly via `invoke_subagent` and `send_message`.
- **Native Swarm Mode**: The Orchestrator spawns `@pm`, `@sa`, `@uiux`, `@dev`, and `@qa` via the `swarm` tool, where agents hand off directly on the shared message board using `@mentions`.
