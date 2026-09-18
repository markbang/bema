import SharedLogic
import UIKit

/// The memo detail screen: the memo, its replies, and a reply panel that rises
/// with the keyboard. Chrome comes from the navigation bar.
final class MemoDetailViewController: ContentListViewController {
    private enum Item {
        case memo(Memo)
        case repliesHeader(Int)
        case reply(Memo)
    }

    private let controller: MemosTimelineController
    private let memoName: String
    private var startComment: Bool
    private var observation: IosObservation?
    private var state: MemosAppState?
    private var items: [Item] = []

    private let commentPanel = UIView()
    private let commentComposer = MarkdownComposerView(
        placeholder: "Write your reply in Markdown",
        editorHeight: 120...120
    )
    private let sendButton = UIButton(type: .system)
    private var panelVisible = false

    init(controller: MemosTimelineController, memoName: String, startComment: Bool) {
        self.controller = controller
        self.memoName = memoName
        self.startComment = startComment
        super.init(nibName: nil, bundle: nil)
        // The Compose detail has no bottom navigation either.
        hidesBottomBarWhenPushed = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Memo"
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "arrowshape.turn.up.left"),
            primaryAction: UIAction { [weak self] _ in self?.setCommentPanel(visible: true, animated: true) }
        )
        navigationItem.rightBarButtonItem?.accessibilityLabel = "Write a reply"
        configureCommentPanel()

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            self?.apply(state)
        }
        Task { [weak self] in
            guard let self else { return }
            try? await self.controller.openMemo(name: self.memoName)
        }
        if startComment {
            startComment = false
            setCommentPanel(visible: true, animated: false)
        }
    }

    deinit {
        observation?.cancel()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if isMovingFromParent { controller.closeMemo() }
    }

    private func configureCommentPanel() {
        commentPanel.backgroundColor = Palette.inkElevated
        commentPanel.translatesAutoresizingMaskIntoConstraints = false
        commentPanel.isHidden = true
        view.addSubview(commentPanel)

        var cancelConfiguration = UIButton.Configuration.plain()
        cancelConfiguration.title = "Cancel"
        let cancel = UIButton(type: .system)
        cancel.configuration = cancelConfiguration
        cancel.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.commentComposer.setText("")
            self.setCommentPanel(visible: false, animated: true)
        }, for: .touchUpInside)

        var sendConfiguration = UIButton.Configuration.filled()
        sendConfiguration.title = "Reply"
        sendConfiguration.baseBackgroundColor = Palette.accent
        sendButton.configuration = sendConfiguration
        sendButton.addAction(UIAction { [weak self] _ in self?.sendComment() }, for: .touchUpInside)

        let buttons = UIStackView(arrangedSubviews: [cancel, sendButton])
        buttons.axis = .horizontal
        buttons.spacing = 12

        let stack = UIStackView(arrangedSubviews: [commentComposer, buttons])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        commentPanel.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: commentPanel.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: commentPanel.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: commentPanel.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: commentPanel.safeAreaLayoutGuide.bottomAnchor, constant: -16),

            commentPanel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            commentPanel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            // Rises with the keyboard, like Compose's imePadding.
            commentPanel.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])

        commentComposer.onTextChange = { [weak self] value in
            guard let self else { return }
            self.sendButton.isEnabled = !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        sendButton.isEnabled = false
    }

    private func setCommentPanel(visible: Bool, animated: Bool) {
        panelVisible = visible
        tableView.contentInset.bottom = visible ? 320 : 0
        guard animated else {
            commentPanel.isHidden = !visible
            return
        }
        if visible {
            commentPanel.isHidden = false
            commentPanel.transform = CGAffineTransform(translationX: 0, y: 320)
            UIView.animate(withDuration: 0.25) { self.commentPanel.transform = .identity }
        } else {
            UIView.animate(withDuration: 0.25) {
                self.commentPanel.transform = CGAffineTransform(translationX: 0, y: 320)
            } completion: { _ in
                self.commentPanel.isHidden = true
                self.commentPanel.transform = .identity
            }
        }
    }

    private func sendComment() {
        let content = commentComposer.text
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        commentComposer.setText("")
        sendButton.isEnabled = false
        setCommentPanel(visible: false, animated: true)
        Task { [weak self] in
            guard let self else { return }
            try? await self.controller.comment(content: content)
        }
    }

    private func apply(_ state: MemosAppState) {
        self.state = state
        var items: [Item] = []
        if let memo = state.selectedMemo { items.append(.memo(memo)) }
        if !state.selectedComments.isEmpty { items.append(.repliesHeader(state.selectedComments.count)) }
        items.append(contentsOf: state.selectedComments.map(Item.reply))
        self.items = items
        tableView.reloadData()
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        items.count
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard indexPath.row < items.count else { return UITableViewCell() }
        switch items[indexPath.row] {
        case .memo(let memo):
            return memoCell(tableView, indexPath, memo)
        case .repliesHeader(let count):
            let cell = tableView.dequeueReusableCell(withIdentifier: RepliesHeaderCell.reuseIdentifier, for: indexPath) as! RepliesHeaderCell
            cell.configure(count: count)
            return cell
        case .reply(let memo):
            return memoCell(tableView, indexPath, memo)
        }
    }

    private func memoCell(_ tableView: UITableView, _ indexPath: IndexPath, _ memo: Memo) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: MemoTweetCell.reuseIdentifier, for: indexPath) as! MemoTweetCell
        cell.configure(
            memo: memo,
            user: state?.userProfiles[username(of: memo.creator)],
            controller: controller,
            liked: memo.isLikedBy(account: state?.activeAccount),
            actions: MemoActions(
                open: nil,
                reply: { [weak self] in self?.setCommentPanel(visible: true, animated: true) },
                react: { [weak self] in
                    guard let self else { return }
                    Task { try? await self.controller.react(memo: memo, reactionType: heartReaction) }
                },
                openImage: { [weak self] index in self?.openImage(memo: memo, index: index) }
            )
        )
        return cell
    }

    private func openImage(memo: Memo, index: Int) {
        let viewer = ImageViewerViewController(controller: controller, memo: memo, startIndex: index)
        viewer.modalPresentationStyle = .fullScreen
        present(viewer, animated: true)
    }
}
