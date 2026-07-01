# Darrbi → iOS (Compose Multiplatform) migration

This document tracks the staged migration of the Darrbi Android app to a cross-platform
**Compose Multiplatform (CMP)** codebase that runs on Android **and** iOS, sharing UI + logic.

> **Status:** Foundation landed. The CMP toolchain is wired end-to-end and a real shared screen
> (branded home dashboard) builds and runs on **both** the Android emulator and the iOS simulator.
> The 28.6k-LOC feature set is migrated in stages (below) — this is multi-week work, not a one-shot.

---

## 1. Architecture decision

- **Shared UI + logic** via Compose Multiplatform (JetBrains), not a logic-only KMP core. Compose
  screens, the design system, view models and data layer all live in `commonMain` over time.
- The existing **`:app`** module stays the production Android app and is **untouched** during the
  migration. Features are ported into `:composeApp` incrementally; once a feature reaches parity, the
  `:app` screen can delegate to (or be replaced by) the shared one. No big-bang rewrite.

### Module layout

```
:app          Existing production Android app (Jetpack Compose + Hilt + Retrofit + Maps). Unchanged.
:composeApp   KMP shared LIBRARY — commonMain (shared Compose UI + logic), androidMain, iosMain.
              Exports the static `ComposeApp.framework` for iOS.
:androidApp   Thin Android application (appId com.mytm.darrbi.mp) hosting the shared App() — lets us
              run/verify the shared UI on Android during migration.
iosApp/       Xcode project (SwiftUI) that hosts the shared App() via ComposeUIViewController.
              Generated from project.yml with XcodeGen.
```

### Version pins (the critical, non-obvious part)

AGP 9 and Kotlin/Native put us in a narrow compatibility window:

| Tool | Version | Why this exact version |
|---|---|---|
| AGP | 9.0.1 | inherited from `:app` |
| Kotlin | 2.2.10 | inherited from `:app` (KSP `2.2.10-2.0.2`, Hilt 2.59.2 depend on it) |
| Compose Multiplatform | **1.10.3** | see below |

- AGP 9.0 **forbids** `com.android.application`/`com.android.library` together with the KMP plugin.
  `:composeApp` therefore uses the new **`com.android.kotlin.multiplatform.library`** plugin
  (`androidLibrary { … }` DSL), and the launchable Android app is the separate `:androidApp`.
- **CMP 1.11.x** fails: its plugin calls an AGP variant API (`onVariant`) only in the older shape, and
  its iOS klibs are built with **Kotlin 2.3.20** (klib ABI 2.3.0) which Kotlin 2.2.10 cannot consume.
- **CMP 1.9.0** fails the other way: no support for AGP 9's new KMP library plugin (`onVariant`).
- **CMP 1.10.3** is the sweet spot: supports AGP 9's `androidLibrary`, and its iOS klibs are
  Kotlin-2.2-ABI-compatible. **Do not bump CMP without also bumping Kotlin** (which then drags KSP +
  Hilt + `:app` along — out of scope for the migration).

---

## 2. What already works (foundation)

- `:composeApp/commonMain` shares the **design system** verbatim: `DarrbiColors`, `DarrbiTypography`,
  `DarrbiTheme` (pure Compose, zero changes), plus locale-aware **fonts** (SF Pro / Madani) loaded via
  CMP resources (`composeResources/font/*`, generated `Res` class).
- A real shared screen — the branded **home dashboard** (`App.kt`) — renders identically on Android and iOS.
- `expect/actual` proven via `platformName()` (Android / iOS).
- Builds verified: `:androidApp:assembleDebug` (APK) and `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  (iOS framework), plus the `iosApp` Xcode build via `embedAndSignAppleFrameworkForXcode`.

---

## 3. Dependency migration (Android-only → multiplatform)

Each Android-only library in `:app` needs a multiplatform replacement before the code that uses it can
move to `commonMain`. Ordered by dependency (do top-down):

| Concern | `:app` today | Multiplatform target | Notes / effort |
|---|---|---|---|
| Serialization | kotlinx.serialization | **same** (already MP) | none — reuse DTOs as-is |
| Networking | Retrofit + OkHttp | **Ktor client** (`darwin` + `okhttp` engines) | Rewrite the qualified multi-`Retrofit` setup + `ApiResult`/envelopes as Ktor services. ~14 files. Medium-large. |
| DI | Hilt (KSP) | **Koin** | Hilt is JVM/Android-only. Re-express `@HiltViewModel`/modules as Koin modules. ~49 files. Large but mechanical. |
| ViewModel | androidx.lifecycle.ViewModel | `androidx.lifecycle` **CMP** viewmodel (`org.jetbrains.androidx.lifecycle`) | CMP ships a multiplatform ViewModel + `viewModel { }`. State holders move to commonMain. |
| Real-time | `io.socket:socket.io-client` (JVM) | **Ktor WebSockets** (or a Swift Socket.IO client behind `expect/actual`) | 1 file but protocol-sensitive (EIO=4). Prototype carefully. Medium. |
| Image loading | Coil 2 | **Coil 3** (KMP) | Drop-in-ish; `AsyncImage` API is similar. Small. |
| Key-value store | DataStore (androidx) | **DataStore multiplatform** (1.1+ has KMP artifacts) | Small. |
| Location | play-services-location | `expect/actual`: FusedLocation (Android) / **CoreLocation** (iOS) | Medium. |
| Places autocomplete | Places SDK (Android) | `expect/actual`: Places Android / **Places iOS SDK** (or backend proxy) | Medium. |
| **Maps** | `maps-compose` (Google) | **`expect/actual` map view**: `maps-compose` on Android; **MapKit** or Google-Maps-iOS via `UIKitView` on iOS | **The hard blocker.** No CMP Google-Maps. Wrap a `@Composable expect fun DarrbiMap(...)` with platform actuals. Large — this gates the rider/trip/pickup/courier/schedule screens. |
| Strings | `R.string` (en/ar) | CMP resources `Res.string.*` | Mechanical but pervasive (the whole UI). Move `strings.xml` → `composeResources/values/strings*.xml`; codemod `stringResource(R.string.x)` → `stringResource(Res.string.x)`. |
| Drawables/raw | `R.drawable` | CMP `Res.drawable.*` / `painterResource` | Move assets into `composeResources/drawable`. |

---

## 4. Suggested feature migration order

Migrate leaf-first so each step is shippable on iOS without the map blocker:

1. **Foundation** ✅ — theme, fonts, resources, build, both platforms running.
2. **Design-system components** — `Buttons`, `Inputs`, `DarrbiCard`, `Selectors`, `CarComponents`
   (mostly pure Compose; move to `commonMain`, swap `R` resources for `Res`).
3. **Networking + DI substrate** — stand up Ktor + Koin in `commonMain`; port one vertical slice
   (auth / OTP / session) end-to-end as the reference implementation.
4. **Non-map screens** — onboarding, profile, settings, wallet/top-up, chat, support, legal, reports,
   ride history. These reach iOS parity without maps.
5. **Maps abstraction** — build `expect/actual DarrbiMap` + location + places. Highest risk; spike early
   in parallel with (4).
6. **Map-centric flows** — rider booking (incl. the pre-trip overlays + back buttons), live trip,
   courier/cargo, city-to-city schedule, car rental. These depend on (5).
7. **Cutover** — point Android at the shared screens; retire duplicated `:app` code per feature.

---

## 5. How to build / run

**Android (shared UI):**
```bash
./gradlew :androidApp:installDebug
adb shell monkey -p com.mytm.darrbi.mp -c android.intent.category.LAUNCHER 1
```

**iOS framework only (fast compile check):**
```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

**iOS app (simulator):**
```bash
# regenerate the Xcode project after editing iosApp/project.yml
cd iosApp && xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build
# then: xcrun simctl install booted <App.app>  &&  xcrun simctl launch booted com.mytm.darrbi.ios
```
The Xcode build runs the Gradle `embedAndSignAppleFrameworkForXcode` phase (JAVA_HOME pinned to
temurin-17 in the build script) to produce/place the `ComposeApp` framework.

---

## 6. Open risks

- **Maps on iOS** — biggest unknown; MapKit lacks some Google features (Places, directions polylines).
  May need the Google Maps iOS SDK (CocoaPods/SPM) wired through `cinterop` or a UIKit wrapper.
- **CMP/Kotlin lockstep** — staying on Kotlin 2.2.10 pins CMP to 1.10.x. A future Kotlin bump must be
  coordinated with `:app` (KSP, Hilt, compose-compiler).
- **Socket.IO EIO=4** — the backend rejected EIO=3; verify Ktor WS (or a native client) negotiates v4.
- **Material icons** — `compose.materialIconsExtended` is pinned to 1.7.3 in CMP; fine, but consider
  migrating heavily-used icons to bundled vector `Res.drawable` to drop the dependency later.
