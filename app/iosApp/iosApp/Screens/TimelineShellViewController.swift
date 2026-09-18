import UIKit

/// Hosts the two tabs, the collapsing header, the bottom navigation and the
/// compose button. Mirrors the Android `TimelineShell`.
final class TimelineShellViewController: UIViewController {
    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var state: MemosAppState?
    private var isSignInPresented = false
    private var shownError: String?

    private let contentView = UIView()
    private let chrome = TopChromeView()
    private let veil = StatusBarVeilView()
    private let bottomNav = BottomNavView()
    private let fab = UIButton(type: .custom)

    private let timelineHeader = TimelineHeaderView()
    private let searchHeader = SearchHeaderView()

    private lazy var timelineVC = TimelineViewController(controller: controller)
    private lazy var searchVC = SearchViewController(controller: controller)

    private var chromeHeight: CGFloat = 0
    private var chromeHeightConstraint: NSLayoutConstraint!
    private var veilHeightConstraint: NSLayoutConstraint!
    private var selectedTab = 0

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink
        configureLayout()
        wireChildren()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
        installHeader()
        select(tab: 0)
    }

    deinit {
        observation?.cancel()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let height = chrome.fittingHeight(width: view.bounds.width)
        if abs(height - chromeHeight) > 0.5 {
            chromeHeight = height
            chromeHeightConstraint.constant = height
            applyChildInsets()
        }
        let veilHeight = view.safeAreaInsets.top + 18
        if abs(veilHeight - veilHeightConstraint.constant) > 0.5 {
            veilHeightConstraint.constant = veilHeight
        }
    }

    private func configureLayout() {
        [contentView, bottomNav, chrome, veil, fab].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }

        chromeHeightConstraint = chrome.heightAnchor.constraint(equalToConstant: 0)
        veilHeightConstraint = veil.heightAnchor.constraint(equalToConstant: 36)

        NSLayoutConstraint.activate([
            contentView.topAnchor.constraint(equalTo: view.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: bottomNav.topAnchor),

            bottomNav.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bottomNav.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bottomNav.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            bottomNav.heightAnchor.constraint(equalToConstant: Metrics.bottomNavHeight),

            chrome.topAnchor.constraint(equalTo: view.topAnchor),
            chrome.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            chrome.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            chromeHeightConstraint,

            veil.topAnchor.constraint(equalTo: view.topAnchor),
            veil.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            veil.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            veilHeightConstraint,

            fab.widthAnchor.constraint(equalToConstant: Metrics.fabSize),
            fab.heightAnchor.constraint(equalToConstant: Metrics.fabSize),
            fab.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            fab.bottomAnchor.constraint(equalTo: bottomNav.topAnchor, constant: -18)
        ])

        fab.backgroundColor = Palette.accent
        fab.setImage(MiuixIcons.image(.add, size: 24, color: .white), for: .normal)
        fab.layer.cornerRadius = Metrics.fabSize / 2
        fab.addAction(UIAction { [weak self] _ in self?.showComposer() }, for: .touchUpInside)

        bottomNav.onSelect = { [weak self] tab in self?.select(tab: tab) }
        bottomNav.onSettings = { [weak self] in self?.showSettings() }
    }

    private func wireChildren() {
        addChild(timelineVC, to: contentView)
        addChild(searchVC, to: contentView)
        searchVC.view.isHidden = true

        timelineVC.onScroll = { [weak self] distance in self?.collapseChrome(distance) }
        timelineVC.onOpen = { [weak self] memo in self?.open(memo: memo, startComment: false) }
        timelineVC.onReply = { [weak self] memo in self?.open(memo: memo, startComment: true) }
        timelineVC.onOpenImage = { [weak self] memo, index in self?.showImage(memo: memo, startIndex: index) }

        searchVC.onScroll = { _ in }
        searchVC.onOpen = { [weak self] memo in self?.open(memo: memo, startComment: false) }
        searchVC.onOpenImage = { [weak self] memo, index in self?.showImage(memo: memo, startIndex: index) }

        timelineHeader.onAccounts = { [weak self] in self?.showAccounts() }
        timelineHeader.onSearch = { [weak self] in self?.select(tab: 1) }
        timelineHeader.onRefresh = { [weak self] in
            guard let self else { return }
            Task { try? await self.controller.refreshTimeline(filter: "") }
        }
    }

    private func addChild(_ child: UIViewController, to container: UIView) {
        addChild(child)
        child.view.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(child.view)
        NSLayoutConstraint.activate([
            child.view.topAnchor.constraint(equalTo: container.topAnchor),
            child.view.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            child.view.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            child.view.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        child.didMove(toParent: self)
    }

    // MARK: - State

    private func apply(_ state: MemosAppState) {
        self.state = state
        guard let account = state.activeAccount else {
            [contentView, bottomNav, chrome, veil, fab].forEach { $0.isHidden = true }
            presentSignIn()
            return
        }
        [contentView, bottomNav, chrome, veil].forEach { $0.isHidden = false }
        fab.isHidden = selectedTab != 0
        timelineHeader.update(account: account, controller: controller)
        searchHeader.update(siteTitle: account.siteTitle)
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
        signIn.modalTransitionStyle = .crossDissolve
        signIn.onSignedIn = { [weak self] in self?.isSignInPresented = false }
        present(signIn, animated: false)
    }

    // MARK: - Tabs

    private func select(tab: Int) {
        selectedTab = tab
        bottomNav.selectedIndex = tab
        timelineVC.view.isHidden = tab != 0
        searchVC.view.isHidden = tab != 1
        fab.isHidden = tab != 0
        installHeader()
        collapseChrome(0)
        applyChildInsets()
        if tab == 0 { timelineVC.appear() }
    }

    private func installHeader() {
        chrome.install(
            selectedTab == 0 ? timelineHeader : searchHeader,
            insets: UIEdgeInsets(top: 10, left: 18, bottom: 10, right: 18)
        )
        view.setNeedsLayout()
    }

    private func applyChildInsets() {
        timelineVC.topInset = chromeHeight
        timelineVC.bottomInset = 20
        searchVC.topInset = chromeHeight
        searchVC.bottomInset = 20
    }

    /// Matches the Android nested-scroll handler: the chrome slides up with the
    /// list and fades out over its own height.
    private func collapseChrome(_ distance: CGFloat) {
        guard chromeHeight > 0 else { return }
        let offset = -min(max(distance, 0), chromeHeight)
        chrome.transform = CGAffineTransform(translationX: 0, y: offset)
        chrome.alpha = max(0, 1 + offset / chromeHeight * 1.15)
    }

    // MARK: - Presentation

    private func open(memo: Memo, startComment: Bool) {
        let detail = MemoDetailViewController(controller: controller, memoName: memo.name, startComment: startComment)
        navigationController?.pushViewController(detail, animated: true)
    }

    private func showImage(memo: Memo, startIndex: Int) {
        let viewer = ImageViewerViewController(controller: controller, memo: memo, startIndex: startIndex)
        viewer.modalPresentationStyle = .fullScreen
        viewer.modalTransitionStyle = .crossDissolve
        present(viewer, animated: true)
    }

    private func showComposer() {
        let composer = ComposerSheetViewController(controller: controller)
        composer.modalPresentationStyle = .overFullScreen
        composer.modalTransitionStyle = .crossDissolve
        present(composer, animated: true)
    }

    private func showAccounts() {
        guard let state else { return }
        let sheet = AccountsSheetViewController(controller: controller, state: state)
        sheet.modalPresentationStyle = .overFullScreen
        sheet.modalTransitionStyle = .crossDissolve
        present(sheet, animated: true)
    }

    private func showSettings() {
        let settings = SettingsSheetViewController(controller: controller)
        settings.modalPresentationStyle = .overFullScreen
        settings.modalTransitionStyle = .crossDissolve
        present(settings, animated: true)
    }
}
