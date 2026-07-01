package com.mytm.darrbi.shared

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point. The Swift `iosApp` hosts this UIViewController (via `ComposeView`) so the shared
 * Compose [App] renders inside a SwiftUI scene. Exported in the "ComposeApp" framework.
 */
fun MainViewController() = ComposeUIViewController { App() }
