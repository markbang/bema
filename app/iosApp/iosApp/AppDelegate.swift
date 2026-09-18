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
        let navigation = UINavigationController(rootViewController: TimelineShellViewController(controller: controller))
        // Every screen draws its own chrome, like the Compose Scaffold does.
        navigation.setNavigationBarHidden(true, animated: false)
        navigation.view.backgroundColor = Palette.ink

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.overrideUserInterfaceStyle = .dark
        window.backgroundColor = Palette.ink
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
