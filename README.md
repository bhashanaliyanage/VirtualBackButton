# 📲 Floating Back Button for Android

> 🚧 **This project is currently under maintenance.**

A lightweight Android service that provides a **floating back button** accessible system-wide. Designed to assist users with easier navigation, especially for devices with large screens or broken hardware buttons.

---

## 🚀 Features

- 🧭 System-wide floating back button
- 👆 Tap to simulate the **BACK** action
- 📳 Vibration feedback on press
- 🔄 Automatically adjusts to screen orientation
- 🛠 Minimal and efficient implementation using `AccessibilityService` and overlay permissions

---

## 🗂️ Project Structure

| File/Path                     | Description                                      |
|-------------------------------|--------------------------------------------------|
| `AndroidManifest.xml`         | Declares permissions and services                |
| `BackButtonService.java`      | Main logic for floating button via accessibility |
| `CheckPermissionService.java` | Verifies overlay/accessibility permissions       |
| `nu.back.button.*`            | Main app package                                 |
| `com.facebook.ads.*`, `Glide` | Third-party libraries (non-essential)            |

---

## 🛡️ Required Permissions

| Permission                     | Purpose                               |
|--------------------------------|---------------------------------------|
| `SYSTEM_ALERT_WINDOW`          | Overlay support                       |
| `BIND_ACCESSIBILITY_SERVICE`   | Enables simulating global BACK action |
| `VIBRATE`                      | Haptic feedback                       |
| `QUERY_ALL_PACKAGES`           | [⚠️ Risky] Scans all installed apps   |
| `CALL_PHONE`, `WRITE_SETTINGS` | [⚠️ Risky] Likely unnecessary         |
| Others (e.g., `INTERNET`)      | Possibly ad or legacy-related         |

---

## 🔄 App Flow

```text
1. Request overlay and accessibility permissions
2. Start BackButtonService
3. Display a floating ImageView via WindowManager
4. Tap = BACK + vibration
5. Auto-adjusts to display/rotation changes
