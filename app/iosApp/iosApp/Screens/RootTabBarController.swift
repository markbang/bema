import SharedLogic
import UIKit

/// The app shell: two tabs, each in its own navigation stack, plus the
/// full-screen sign-in when no account is configured.
final class RootTabBarController: UITabBarController, UITabBarControllerDelegate {
    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var isSignInPresented = false
    private var shownError: String?

    // The activity panel rides above the tab bar, which a child view controller's own
    // subviews cannot cover.
    private let activityScrim = UIView()
    private let activityPanel = ActivityPanelView()
    private var activityOffset: CGFloat = 0
    private var activityWidth: CGFloat = 0

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
        configureActivityPanel()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        activityWidth = view.bounds.width * 0.86
        activityScrim.frame = view.bounds
        positionActivity()
    }

    private func configureActivityPanel() {
        activityScrim.backgroundColor = UIColor.black.withAlphaComponent(0.45)
        activityScrim.isHidden = true
        activityScrim.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(closeActivity)))
        activityScrim.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(dragActivity(_:))))

        activityPanel.isHidden = true
        activityPanel.onDayTap = { [weak self] day in
            guard let self else { return }
            self.closeActivity()
            Task { try? await self.controller.showDay(epochDay: KotlinLong(value: day)) }
        }
        activityPanel.onTagTap = { [weak self] tag in
            guard let self else { return }
            self.closeActivity()
            self.selectedIndex = 1
            self.searchVC.seed(query: tag)
        }
        view.addSubview(activityScrim)
        view.addSubview(activityPanel)

        timelineVC.onActivityDrag = { [weak self] translation, ended in
            guard let self else { return }
            self.moveActivity(to: translation)
            if ended { self.settleActivity() }
        }
    }

    @objc private func dragActivity(_ recognizer: UIPanGestureRecognizer) {
        moveActivity(to: activityWidth + recognizer.translation(in: view).x)
        if recognizer.state == .ended || recognizer.state == .cancelled { settleActivity() }
    }

    @objc private func closeActivity() {
        UIView.animate(withDuration: 0.22) {
            self.activityOffset = 0
            self.positionActivity()
        } completion: { _ in
            self.activityPanel.isHidden = true
            self.activityScrim.isHidden = true
        }
    }

    private func moveActivity(to offset: CGFloat) {
        guard activityWidth > 0 else { return }
        activityOffset = min(max(offset, 0), activityWidth)
        activityPanel.isHidden = false
        activityScrim.isHidden = false
        positionActivity()
    }

    private func settleActivity() {
        let shouldOpen = activityOffset > activityWidth / 2
        UIView.animate(withDuration: 0.22) {
            self.activityOffset = shouldOpen ? self.activityWidth : 0
            self.positionActivity()
        } completion: { _ in
            self.activityPanel.isHidden = !shouldOpen
            self.activityScrim.isHidden = !shouldOpen
        }
        if shouldOpen { Task { try? await self.controller.refreshActivityStats() } }
    }

    private func positionActivity() {
        activityPanel.frame = CGRect(x: -activityWidth + activityOffset, y: 0, width: activityWidth, height: view.bounds.height)
        activityScrim.alpha = activityWidth > 0 ? activityOffset / activityWidth : 0
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
        activityPanel.apply(state.activity)
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
