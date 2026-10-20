import SwiftUI
import UIKit
import OnlyShotsApp

/** Answers which way the screen may turn: the record screen holds it the way its video is filmed. */
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(MainViewControllerKt.supportedOrientations()))
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}
