import SharedLogic
import UIKit

/// The memo detail screen: the memo, its replies, and a reply composer that
/// slides up from the bottom. Mirrors `MemoDetailScreen`.
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

    private let chrome = TopChromeView()
    private let veil = StatusBarVeilView()
    private let backButton = UIButton(type: .custom)
    private let chromeTitle = UILabel()
    private let replyFAB = UIButton(type: .custom)
    private let commentPanel = UIView()
    private let commentComposer = MarkdownComposerView(
        placeholder: "Write your reply in Markdown",
        editorHeight: 120...120
    )
    private let sendButton = TextActionButton(title: "Reply", primary: true)

    private var chromeHeight: CGFloat = 0
    private var chromeHeightConstraint: NSLayoutConstraint!
    private var veilHeightConstraint: NSLayoutConstraint!
    private var panelVisible = false

    init(controller: MemosTimelineController, memoName: String, startComment: Bool) {
        self.controller = controller
        self.memoName = memoName
        self.startComment = startComment
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureChrome()
        configureCommentPanel()
        bottomInset = 28

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

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let height = chrome.fittingHeight(width: view.bounds.width)
        if abs(height - chromeHeight) > 0.5 {
            chromeHeight = height
            chromeHeightConstraint.constant = height
            topInset = height
        }
        let veilHeight = view.safeAreaInsets.top + 18
        if abs(veilHeight - veilHeightConstraint.constant) > 0.5 {
            veilHeightConstraint.constant = veilHeight
        }
    }

    private func configureChrome() {
        chrome.translatesAutoresizingMaskIntoConstraints = false
        veil.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(chrome)
        view.addSubview(veil)

        chromeHeightConstraint = chrome.heightAnchor.constraint(equalToConstant: 0)
        veilHeightConstraint = veil.heightAnchor.constraint(equalToConstant: 36)
        NSLayoutConstraint.activate([
            chrome.topAnchor.constraint(equalTo: view.topAnchor),
            chrome.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            chrome.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            chromeHeightConstraint,
            veil.topAnchor.constraint(equalTo: view.topAnchor),
            veil.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            veil.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            veilHeightConstraint
        ])

        backButton.setImage(MiuixIcons.image(.back, size: 24, color: Palette.textPrimary), for: .normal)
        backButton.accessibilityLabel = "Back"
        backButton.addAction(UIAction { [weak self] _ in
            self?.navigationController?.popViewController(animated: true)
        }, for: .touchUpInside)
        chromeTitle.text = "Memo"
        chromeTitle.font = TextStyle.title3.weight(.bold)
        chromeTitle.textColor = Palette.textPrimary

        let row = UIStackView(arrangedSubviews: [backButton, chromeTitle])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 8
        NSLayoutConstraint.activate([
            backButton.widthAnchor.constraint(equalToConstant: 40),
            backButton.heightAnchor.constraint(equalToConstant: 40)
        ])
        chrome.install(row, insets: UIEdgeInsets(top: 4, left: 10, bottom: 4, right: 10))

        replyFAB.translatesAutoresizingMaskIntoConstraints = false
        replyFAB.backgroundColor = Palette.accent
        replyFAB.setImage(MiuixIcons.image(.reply, size: 24, color: .white), for: .normal)
        replyFAB.layer.cornerRadius = Metrics.fabSize / 2
        replyFAB.accessibilityLabel = "Write a reply"
        replyFAB.addAction(UIAction { [weak self] _ in self?.setCommentPanel(visible: true, animated: true) }, for: .touchUpInside)
        view.addSubview(replyFAB)
        NSLayoutConstraint.activate([
            replyFAB.widthAnchor.constraint(equalToConstant: Metrics.fabSize),
            replyFAB.heightAnchor.constraint(equalToConstant: Metrics.fabSize),
            replyFAB.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            replyFAB.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20)
        ])
    }

    private func configureCommentPanel() {
        commentPanel.backgroundColor = Palette.inkElevated
        commentPanel.translatesAutoresizingMaskIntoConstraints = false
        commentPanel.isHidden = true
        view.addSubview(commentPanel)

        let cancel = TextActionButton(title: "Cancel")
        cancel.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.commentComposer.setText("")
            self.setCommentPanel(visible: false, animated: true)
        }, for: .touchUpInside)
        sendButton.addAction(UIAction { [weak self] _ in self?.sendComment() }, for: .touchUpInside)
        let buttons = UIStackView(arrangedSubviews: [cancel, sendButton])
        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        let stack = sheetStack([commentComposer, buttons], spacing: 12)
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
        replyFAB.isHidden = visible
        bottomInset = visible ? 300 : 28
        guard animated else {
            commentPanel.isHidden = !visible
            return
        }
        if visible {
            commentPanel.isHidden = false
            commentPanel.transform = CGAffineTransform(translationX: 0, y: 320)
            UIView.animate(withDuration: 0.2) { self.commentPanel.transform = .identity }
        } else {
            UIView.animate(withDuration: 0.2) {
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
        viewer.modalTransitionStyle = .crossDissolve
        present(viewer, animated: true)
    }
}
