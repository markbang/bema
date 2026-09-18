import SharedLogic
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // The shared module owns all account and timeline state, so a single
        // controller instance backs the whole UI.
        let controller = MemosTimelineController(keyValueStore: PlatformKeyValueStore.shared)

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.overrideUserInterfaceStyle = .dark
        window.tintColor = Palette.accent
        window.backgroundColor = Palette.ink
        window.rootViewController = RootTabBarController(controller: controller)
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
