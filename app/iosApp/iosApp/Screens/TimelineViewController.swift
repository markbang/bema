import SharedLogic
import UIKit

/// The timeline tab. Renders exactly what the Android `TimelineScreen` does:
/// an optional loading line, error text, the "new memos" banner, then the memos.
/// Chrome comes from the navigation bar; pull to refresh replaces the button.
final class TimelineViewController: ContentListViewController {
    private enum Item {
        case loading
        case error(String)
        case newer
        case memo(Memo)
    }

    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var state: MemosAppState?
    private var items: [Item] = []

    private let avatarButton = UIButton(type: .custom)
    private let refreshControl = UIRefreshControl()
    private var avatarTask: Task<Void, Never>?
    private var loadedAccountId: String?

    /// Reports a sideways drag so the shell can slide the activity panel in; the
    /// timeline itself has no horizontal gesture to compete with.
    var onActivityDrag: ((CGFloat, Bool) -> Void)?

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
        tabBarItem = UITabBarItem(
            title: "Timeline",
            image: UIImage(systemName: "house"),
            selectedImage: UIImage(systemName: "house.fill")
        )
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureNavigation()
        refreshControl.tintColor = Palette.textSecondary
        refreshControl.addTarget(self, action: #selector(refreshPulled), for: .valueChanged)
        tableView.refreshControl = refreshControl
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }

        let activityPan = UIPanGestureRecognizer(target: self, action: #selector(handleActivityPan(_:)))
        activityPan.delegate = self
        view.addGestureRecognizer(activityPan)
    }

    @objc private func handleActivityPan(_ recognizer: UIPanGestureRecognizer) {
        let translation = max(recognizer.translation(in: view).x, 0)
        switch recognizer.state {
        case .changed:
            onActivityDrag?(translation, false)
        case .ended, .cancelled:
            onActivityDrag?(translation, true)
        default:
            break
        }
    }

    deinit {
        observation?.cancel()
    }

    private func configureNavigation() {
        // The avatar is the account switcher, the way the Android header's is.
        avatarButton.frame = CGRect(x: 0, y: 0, width: 32, height: 32)
        avatarButton.layer.cornerRadius = 16
        avatarButton.clipsToBounds = true
        avatarButton.accessibilityLabel = "Accounts"
        avatarButton.addAction(UIAction { [weak self] _ in self?.showAccounts() }, for: .touchUpInside)
        navigationItem.leftBarButtonItem = UIBarButtonItem(customView: avatarButton)
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(
                image: UIImage(systemName: "square.and.pencil"),
                primaryAction: UIAction { [weak self] _ in self?.showComposer() }
            ),
            UIBarButtonItem(
                image: UIImage(systemName: "gearshape"),
                primaryAction: UIAction { [weak self] _ in self?.showSettings() }
            )
        ]
        navigationItem.largeTitleDisplayMode = .never
    }

    /// Compose re-runs its load effect whenever the tab re-enters view; the tab
    /// bar controller calls this on the same transitions.
    func appear() {
        if state?.timeline.isEmpty ?? true {
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.refreshTimeline(filter: "")
            }
        } else {
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.revalidateTimeline()
            }
        }
    }

    private func apply(_ state: MemosAppState) {
        self.state = state
        if let account = state.activeAccount {
            navigationItem.title = account.siteTitle
            navigationItem.prompt = account.visibleName
            loadAvatar(account)
        }
        var items: [Item] = []
        if state.isLoading && state.timeline.isEmpty { items.append(.loading) }
        if let error = state.error { items.append(.error(error)) }
        if state.hasNewerMemos { items.append(.newer) }
        items.append(contentsOf: state.timeline.map(Item.memo))
        if state.isLoadingMore { items.append(.loading) }
        self.items = items
        tableView.reloadData()
        if refreshControl.isRefreshing, !state.isLoading { refreshControl.endRefreshing() }
    }

    private func loadAvatar(_ account: MemosAccount) {
        guard loadedAccountId != account.id else { return }
        loadedAccountId = account.id
        avatarTask?.cancel()
        avatarTask = Task { [weak self] in
            guard let self else { return }
            let bytes = try? await self.controller.accountAvatarBytes(account: account)
            guard !Task.isCancelled else { return }
            let image = decodedImage(from: bytes)
            self.avatarButton.setImage(image ?? UIImage(systemName: "person.crop.circle"), for: .normal)
        }
    }

    @objc private func refreshPulled() {
        Task { [weak self] in
            guard let self else { return }
            try? await self.controller.refreshTimeline(filter: "")
        }
    }

    private func showComposer() {
        presentSheet(ComposerSheetViewController(controller: controller), detents: [.large()])
    }

    private func showAccounts() {
        guard let state else { return }
        presentSheet(AccountsSheetViewController(controller: controller, state: state), detents: [.medium(), .large()])
    }

    private func showSettings() {
        presentSheet(SettingsViewController(controller: controller), detents: [.large()])
    }

    private func openMemo(_ memo: Memo, startComment: Bool) {
        navigationController?.pushViewController(
            MemoDetailViewController(controller: controller, memoName: memo.name, startComment: startComment),
            animated: true
        )
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        items.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard indexPath.row < items.count else { return UITableViewCell() }
        switch items[indexPath.row] {
        case .loading:
            return tableView.dequeueReusableCell(withIdentifier: LoadingCell.reuseIdentifier, for: indexPath)
        case .error(let message):
            let cell = tableView.dequeueReusableCell(withIdentifier: ErrorCell.reuseIdentifier, for: indexPath) as! ErrorCell
            cell.configure(message: message)
            return cell
        case .newer:
            let cell = tableView.dequeueReusableCell(withIdentifier: NewerMemosCell.reuseIdentifier, for: indexPath) as! NewerMemosCell
            cell.setAction { [weak self] in
                guard let self else { return }
                Task { try? await self.controller.refreshTimeline(filter: "") }
            }
            return cell
        case .memo(let memo):
            let cell = tableView.dequeueReusableCell(withIdentifier: MemoTweetCell.reuseIdentifier, for: indexPath) as! MemoTweetCell
            cell.configure(
                memo: memo,
                user: state?.userProfiles[username(of: memo.creator)],
                controller: controller,
                liked: memo.isLikedBy(account: state?.activeAccount),
                actions: MemoActions(
                    open: { [weak self] in self?.openMemo(memo, startComment: false) },
                    reply: { [weak self] in self?.openMemo(memo, startComment: true) },
                    react: { [weak self] in
                        guard let self else { return }
                        Task { try? await self.controller.react(memo: memo, reactionType: heartReaction) }
                    },
                    openImage: { [weak self] index in self?.openImage(memo: memo, index: index) }
                )
            )
            return cell
        }
    }

    override func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        guard let state, state.canLoadMore, indexPath.row >= items.count - 4 else { return }
        Task { try? await controller.loadMore() }
    }

    private func openImage(memo: Memo, index: Int) {
        let viewer = ImageViewerViewController(controller: controller, memo: memo, startIndex: index)
        viewer.modalPresentationStyle = .fullScreen
        present(viewer, animated: true)
    }
}

extension TimelineViewController: UIGestureRecognizerDelegate {
    /// Only sideways drags belong to the activity panel; vertical ones stay with the
    /// list, which is what the delegate is for.
    func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
        guard let pan = recognizer as? UIPanGestureRecognizer else { return true }
        let velocity = pan.velocity(in: view)
        return abs(velocity.x) > abs(velocity.y)
    }
}
