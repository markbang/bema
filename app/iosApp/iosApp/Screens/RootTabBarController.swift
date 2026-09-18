import SharedLogic
import UIKit

/// The app shell: two tabs, each in its own navigation stack, plus the
/// full-screen sign-in when no account is configured.
final class RootTabBarController: UITabBarController, UITabBarControllerDelegate {
    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var isSignInPresented = false
    private var shownError: String?

    private lazy var timelineVC = TimelineViewController(controller: controller)
    private lazy var searchVC = SearchViewController(controller: controller)

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        delegate = self
        viewControllers = [
            UINavigationController(rootViewController: timelineVC),
            UINavigationController(rootViewController: searchVC)
        ]
        configureBars()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
    }

    deinit {
        observation?.cancel()
    }

    private func configureBars() {
        // Transparent at the scroll edge so content shows through, with the
        // system's own material once a list scrolls under it — the native
        // equivalent of the Android gradient chrome.
        let scrolled = UINavigationBarAppearance()
        scrolled.configureWithDefaultBackground()
        let atEdge = UINavigationBarAppearance()
        atEdge.configureWithTransparentBackground()

        for case let navigation as UINavigationController in viewControllers ?? [] {
            navigation.navigationBar.standardAppearance = scrolled
            navigation.navigationBar.compactAppearance = scrolled
            navigation.navigationBar.scrollEdgeAppearance = atEdge
            navigation.navigationBar.tintColor = Palette.accent
        }
        tabBar.tintColor = Palette.accent
        tabBar.unselectedItemTintColor = Palette.textSecondary
        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithDefaultBackground()
        tabBar.standardAppearance = tabAppearance
        tabBar.scrollEdgeAppearance = tabAppearance
    }

    func tabBarController(_ tabBarController: UITabBarController, didSelect viewController: UIViewController) {
        // Android re-runs the timeline's load effect each time the tab is entered.
        if viewController === viewControllers?.first { timelineVC.appear() }
    }

    private func apply(_ state: MemosAppState) {
        guard state.activeAccount != nil else {
            presentSignIn()
            return
        }
        if let error = state.error, error != shownError {
            shownError = error
            presentError(error, from: self)
        }
    }

    private func presentSignIn() {
        guard !isSignInPresented else { return }
        isSignInPresented = true
        let signIn = SignInViewController(controller: controller)
        signIn.modalPresentationStyle = .fullScreen
        signIn.onSignedIn = { [weak self] in self?.isSignInPresented = false }
        present(signIn, animated: false)
    }
}
