import UIKit

/// The search tab. Mirrors the Android `SearchScreen`: a debounced CEL query
/// against `listMemos`, with the empty / searching / no-results states.
final class SearchViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    var onScroll: ((CGFloat) -> Void)?
    var onOpen: ((Memo) -> Void)?
    var onOpenImage: ((Memo, Int) -> Void)?

    var topInset: CGFloat = 0 {
        didSet { fieldTop.constant = topInset }
    }

    var bottomInset: CGFloat = 0 {
        didSet { applyBottomInset() }
    }

    private let controller: MemosTimelineController
    private var observation: IosObservation?
    private var state: MemosAppState?
    private var searchTask: Task<Void, Never>?

    private let field = DarkTextField(placeholder: "Search memos", icon: .search)
    private let countLabel = UILabel()
    private let messageLabel = UILabel()
    private let tableView = UITableView(frame: .zero, style: .plain)
    private var fieldTop: NSLayoutConstraint!

    private var results: [Memo] = []
    private var isSearching = false
    private var searchError: String?

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.state = state
            self?.tableView.reloadData()
        }
        configureViews()
        render()
    }

    deinit {
        observation?.cancel()
    }

    private func configureViews() {
        field.addTarget(self, action: #selector(queryChanged), for: .editingChanged)
        field.translatesAutoresizingMaskIntoConstraints = false

        countLabel.font = TextStyle.footnote1
        countLabel.textColor = Palette.textSecondary
        countLabel.translatesAutoresizingMaskIntoConstraints = false

        messageLabel.font = TextStyle.paragraph
        messageLabel.textColor = Palette.textSecondary
        messageLabel.textAlignment = .center
        messageLabel.numberOfLines = 0
        messageLabel.translatesAutoresizingMaskIntoConstraints = false

        tableView.backgroundColor = Palette.ink
        tableView.separatorStyle = .none
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 320
        tableView.keyboardDismissMode = .interactive
        tableView.contentInsetAdjustmentBehavior = .never
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(MemoTweetCell.self, forCellReuseIdentifier: MemoTweetCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(field)
        view.addSubview(countLabel)
        view.addSubview(tableView)
        view.addSubview(messageLabel)

        fieldTop = field.topAnchor.constraint(equalTo: view.topAnchor, constant: topInset)
        NSLayoutConstraint.activate([
            fieldTop,
            field.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            field.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            countLabel.topAnchor.constraint(equalTo: field.bottomAnchor, constant: 8),
            countLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            countLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            tableView.topAnchor.constraint(equalTo: countLabel.bottomAnchor, constant: 8),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            messageLabel.centerYAnchor.constraint(equalTo: tableView.centerYAnchor),
            messageLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            messageLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32)
        ])
        applyBottomInset()
    }

    private func applyBottomInset() {
        tableView.contentInset.bottom = bottomInset
        tableView.verticalScrollIndicatorInsets.bottom = bottomInset
    }

    @objc private func queryChanged() {
        schedule(field.text ?? "")
    }

    /// Android waits for the keyboard to settle before hitting `listMemos`.
    private func schedule(_ query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            results = []
            searchError = nil
            isSearching = false
            render()
            return
        }
        isSearching = true
        searchError = nil
        render()
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard let self, !Task.isCancelled else { return }
            do {
                let found = try await self.controller.search(query: trimmed)
                guard !Task.isCancelled else { return }
                self.results = found
            } catch {
                guard !Task.isCancelled else { return }
                self.searchError = error.localizedDescription
            }
            self.isSearching = false
            self.render()
        }
    }

    private func render() {
        let query = (field.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        messageLabel.isHidden = false
        countLabel.isHidden = true

        switch true {
        case query.isEmpty:
            messageLabel.text = "Search your memos by keyword"
        case isSearching:
            messageLabel.text = "Searching…"
        case searchError != nil:
            messageLabel.text = searchError
            messageLabel.textColor = Palette.danger
        case results.isEmpty:
            messageLabel.text = "No memos match \"\(query)\""
        default:
            messageLabel.isHidden = true
            countLabel.isHidden = false
            countLabel.text = "\(results.count) \(results.count == 1 ? "result" : "results")"
        }
        if searchError == nil { messageLabel.textColor = Palette.textSecondary }
        tableView.reloadData()
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        onScroll?(scrollView.contentOffset.y + scrollView.adjustedContentInset.top)
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        guard !isSearching, searchError == nil, !(field.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return 0
        }
        return results.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: MemoTweetCell.reuseIdentifier, for: indexPath) as! MemoTweetCell
        guard indexPath.row < results.count else { return cell }
        let memo = results[indexPath.row]
        cell.configure(
            memo: memo,
            user: state?.userProfiles[username(of: memo.creator)],
            controller: controller,
            liked: memo.isLikedBy(account: state?.activeAccount),
            actions: MemoActions(
                open: { [weak self] in self?.onOpen?(memo) },
                // Android routes Reply to open here rather than to the composer.
                reply: { [weak self] in self?.onOpen?(memo) },
                react: { [weak self] in
                    guard let self else { return }
                    Task { try? await self.controller.react(memo: memo, reactionType: heartReaction) }
                },
                openImage: { [weak self] index in self?.onOpenImage?(memo, index) }
            )
        )
        return cell
    }
}
