import UIKit

/// The timeline tab. Renders exactly what the Android `TimelineScreen` does:
/// an optional loading line, error text, the "new memos" banner, then the memos.
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

    var onOpen: ((Memo) -> Void)?
    var onReply: ((Memo) -> Void)?
    var onOpenImage: ((Memo, Int) -> Void)?

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
    }

    deinit {
        observation?.cancel()
    }

    /// Compose re-runs its load effect whenever the tab re-enters composition;
    /// the shell calls this on the same transitions.
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
        var items: [Item] = []
        if state.isLoading && state.timeline.isEmpty { items.append(.loading) }
        if let error = state.error { items.append(.error(error)) }
        if state.hasNewerMemos { items.append(.newer) }
        items.append(contentsOf: state.timeline.map(Item.memo))
        if state.isLoadingMore { items.append(.loading) }
        self.items = items
        tableView.reloadData()
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
                actions: actions(for: memo)
            )
            return cell
        }
    }

    override func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        guard let state, state.canLoadMore, indexPath.row >= items.count - 4 else { return }
        Task { try? await controller.loadMore() }
    }

    private func actions(for memo: Memo) -> MemoActions {
        MemoActions(
            open: { [weak self] in self?.onOpen?(memo) },
            reply: { [weak self] in self?.onReply?(memo) },
            react: { [weak self] in
                guard let self else { return }
                Task { try? await self.controller.react(memo: memo, reactionType: heartReaction) }
            },
            openImage: { [weak self] index in self?.onOpenImage?(memo, index) }
        )
    }
}
