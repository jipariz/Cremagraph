import SwiftUI
import UIKit
import OnlyShotsApp

/**
 * Answers which way the screen may turn (the record screen holds it the way its video is filmed), and sets up the
 * export's background run and the taps on its banners before anything can need them.
 */
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if #available(iOS 26.0, *) {
            platform.IosBackground.shared.continued = ContinuedExport()
        }
        platform.ExportNotificationTaps.shared.install()
        return true
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(supportedOrientations()))
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
