import SharedLogic
import UIKit

/// The search tab. The system search controller owns the field and where it
/// sits; the debounced CEL query behind it is unchanged.
final class SearchViewController: ContentListViewController, UISearchResultsUpdating {
    private enum Item {
        case loading
        case memo(Memo)
    }

    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var state: MemosAppState?
    private var searchTask: Task<Void, Never>?

    private let searchController = UISearchController(searchResultsController: nil)
    private var items: [Item] = []
    private var query = ""
    private var isSearching = false
    private var searchError: String?

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
        tabBarItem = UITabBarItem(
            title: "Search",
            image: UIImage(systemName: "magnifyingglass"),
            selectedImage: UIImage(systemName: "magnifyingglass")
        )
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.title = "Search"
        navigationItem.largeTitleDisplayMode = .never
        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "Search memos"
        searchController.searchBar.searchBarStyle = .minimal
        searchController.searchBar.tintColor = Palette.accent
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.state = state
            self?.tableView.reloadData()
        }
        render()
    }

    deinit {
        observation?.cancel()
    }

    func updateSearchResults(for searchController: UISearchController) {
        schedule(searchController.searchBar.text ?? "")
    }

    /// Android waits for the keyboard to settle before hitting `listMemos`.
    private func schedule(_ text: String) {
        searchTask?.cancel()
        query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            items = []
            searchError = nil
            isSearching = false
            render()
            return
        }
        isSearching = true
        searchError = nil
        items = [.loading]
        render()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard let self, !Task.isCancelled else { return }
            do {
                let found = try await self.controller.search(query: self.query)
                guard !Task.isCancelled else { return }
                self.items = found.map(Item.memo)
            } catch {
                guard !Task.isCancelled else { return }
                self.searchError = error.localizedDescription
                self.items = []
            }
            self.isSearching = false
            self.render()
        }
    }

    private func render() {
        if let searchError {
            showPlaceholder(searchError, tint: Palette.danger)
        } else if query.isEmpty {
            showPlaceholder("Search your memos by keyword")
        } else if !isSearching && items.isEmpty {
            showPlaceholder("No memos match \"\(query)\"")
        } else {
            clearPlaceholder()
        }
        navigationItem.prompt = items.isEmpty ? nil : "\(items.count) \(items.count == 1 ? "result" : "results")"
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
        case .memo(let memo):
            let cell = tableView.dequeueReusableCell(withIdentifier: MemoTweetCell.reuseIdentifier, for: indexPath) as! MemoTweetCell
            cell.configure(
                memo: memo,
                user: state?.userProfiles[username(of: memo.creator)],
                controller: controller,
                liked: memo.isLikedBy(account: state?.activeAccount),
                actions: MemoActions(
                    open: { [weak self] in self?.openMemo(memo, startComment: false) },
                    // Android routes Reply to open here rather than to a composer.
                    reply: { [weak self] in self?.openMemo(memo, startComment: false) },
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

    private func openMemo(_ memo: Memo, startComment: Bool) {
        navigationController?.pushViewController(
            MemoDetailViewController(controller: controller, memoName: memo.name, startComment: startComment),
            animated: true
        )
    }

    private func openImage(memo: Memo, index: Int) {
        let viewer = ImageViewerViewController(controller: controller, memo: memo, startIndex: index)
        viewer.modalPresentationStyle = .fullScreen
        present(viewer, animated: true)
    }
}
