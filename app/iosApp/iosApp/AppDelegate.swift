import SharedLogic
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var themeObservation: IosObservation?
    // The shared module owns all account and timeline state, so a single
    // controller instance backs the whole UI.
    private let controller = MemosTimelineController(keyValueStore: PlatformKeyValueStore.shared)

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window
        window.tintColor = Palette.accent
        window.backgroundColor = Palette.ink
        window.rootViewController = RootTabBarController(controller: controller)
        apply(themeMode: IosInterop.shared.themeMode(controller: controller))
        // The palette is dynamic; only the interface style needs to be pushed when
        // the shared appearance preference changes.
        themeObservation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(themeMode: state.themeMode)
        }
        window.makeKeyAndVisible()
        return true
    }

    private func apply(themeMode: ThemeMode) {
        window?.overrideUserInterfaceStyle = themeMode.interfaceStyle
    }
}
