import UIKit
import SwiftUI
import ComposeApp

/// Bridges the shared Kotlin Compose UI (exported as the `ComposeApp` framework) into SwiftUI.
/// `MainViewController()` is the Kotlin entry point in composeApp/src/iosMain.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
