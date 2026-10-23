# Redmi Note 8 Pro (MIUI / Android 11) Optimization Plan

Enhances **ScreenTime Guardian** to ensure reliable background persistence, timing accuracy, and dialog rendering on Xiaomi / Redmi devices running MIUI on Android 11.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by the user in Phase 1:
> - **Persistent Foreground Service Notification**: Enabled to keep the monitor process active and immune to MIUI's aggressive Low Memory Killer (LMK) during demanding games like *eFootball*.
> - **Dual-Mode Warning Overlay**: Enabled. The app will launch the transparent dialog activity and support a direct system `WindowManager.addView` overlay fallback when overlay permission is granted, ensuring the warning displays over fullscreen games even if MIUI restricts background activity starts.

---

## 1. Overview & Core Concept

- **What It Does**: Hardens the parental control monitor for Redmi Note 8 Pro (MIUI 12 / Android 11) devices. Guarantees that session timers do not freeze when games consume high RAM, and ensures warning dialogs appear over fullscreen games before clearing the game and returning home.
- **Target Persona**: Parents setting screen time rules on Xiaomi/Redmi devices where MIUI's default aggressive memory and permission management would otherwise terminate standard accessibility timers.
- **Key Value**: 100% reliable enforcement without unexpected service termination or silent overlay failures.

---

## 2. User Experience & Visual Design

- **Persistent Service Notification**:
  - Low-priority, clean system notification: *"ScreenTime Guardian: Active"* with quick access to the Parent Dashboard.
  - Can be minimized via system notification settings, but remains active in the system process table to prevent MIUI termination.
- **Dual-Mode Warning Dialog**:
  - **Primary**: Smooth animated Material 3 dialog activity (`TimeLimitWarningActivity`) with the parent's custom message.
  - **Fallback / Overlay View**: If MIUI restricts background activity launch, the service immediately mounts an overlay view directly via `WindowManager` (`TYPE_APPLICATION_OVERLAY`) with identical styling, warning message, and a "Return Home" button.
- **MIUI Optimization Card**:
  - Direct deep-links to:
    1. *MIUI Autostart Management* (`com.miui.permcenter.autostart.AutoStartManagementActivity`).
    2. *MIUI Background Pop-up Permission* (`com.miui.permcenter.permissions.PermissionsEditorActivity`).
    3. *MIUI Battery Saver "No restrictions"* (`com.miui.powerkeeper`).
    4. *Lock in Recents guidance*.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Persistent Foreground Service vs. Background Accessibility Alone**
  - *Chosen Approach*: Register a foreground service with a low-importance ongoing notification.
  - *Why*: MIUI aggressively kills background accessibility services during high-RAM gaming sessions (like *eFootball* or 3D games). A foreground service raises the OOM adjustment score, preventing the system from killing the guardian.
- **Decision 2: Dual-Mode Warning Delivery**
  - *Chosen Approach*: Attempt `TimeLimitWarningActivity` launch, and if overlay permission (`SYSTEM_ALERT_WINDOW`) is active, also support drawing an overlay directly via `WindowManager`.
  - *Why*: MIUI on Android 11 features a proprietary "Display pop-up windows while running in the background" toggle that can block `startActivity` from services. A direct window overlay provides an unbreakable fallback.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                   Redmi Note 8 Pro (MIUI 11)                │
│                                                             │
│   ┌──────────────────────────┐    ┌─────────────────────┐   │
│   │ AppWatcherAccessibility  │◄───┤ Persistent FG Service│  │
│   │ Service (Event Listener) │    │ Notification Channel│   │
│   └────────────┬─────────────┘    └─────────────────────┘   │
│                │                                            │
│                ▼                                            │
│   ┌──────────────────────────┐                              │
│   │ Session Limit Reached    │                              │
│   └────────────┬─────────────┘                              │
│                │                                            │
│       ┌────────┴────────┐                                   │
│       ▼                 ▼                                   │
│ ┌───────────────┐ ┌───────────────────┐                     │
│ │ Activity Mode │ │ WindowManager     │ (Dual Mode Overlay) │
│ │ Dialog Window │ │ Overlay View (M3) │                     │
│ └───────┬───────┘ └─────────┬─────────┘                     │
│         │                   │                               │
│         ▼                   ▼                               │
│ ┌─────────────────────────────────────┐                     │
│ │ Perform GLOBAL_ACTION_HOME          │                     │
│ │ Kill Game Background Process        │                     │
│ └─────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────┘
```

- **Target SDK & Manifest Alignment**:
  - Keep `minSdk = 24`, target `compileSdk = 36`, compatible with Android 11 (API 30).
  - Declare `FOREGROUND_SERVICE` and `SYSTEM_ALERT_WINDOW` permissions.
  - Implement `GuardianOverlayService` or integrated overlay view in `AppWatcherAccessibilityService`.
- **MIUI Permission Intents**:
  - Provide tested explicit component intents for MIUI Security Center and PowerKeeper.
