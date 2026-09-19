import SharedLogic
import UIKit

/// One row of the account list: avatar, site title, account name, and a check
/// when it is the active one.
final class AccountRowView: UIView {
    let account: MemosAccount
    private let onTap: () -> Void
    private let onHold: () -> Void

    init(
        account: MemosAccount,
        isActive: Bool,
        controller: MemosTimelineController,
        onTap: @escaping () -> Void,
        onHold: @escaping () -> Void
    ) {
        self.account = account
        self.onTap = onTap
        self.onHold = onHold
        super.init(frame: .zero)

        let avatar = AvatarImageView()
        avatar.translatesAutoresizingMaskIntoConstraints = false
        avatar.setAvatar(nil, label: account.visibleName)
        Task { [weak avatar] in
            let bytes = try? await controller.accountAvatarBytes(account: account)
            guard !Task.isCancelled else { return }
            avatar?.setAvatar(decodedImage(from: bytes), label: account.visibleName)
        }

        let title = UILabel()
        title.text = account.siteTitle
        title.font = TextStyle.paragraph.weight(.semibold)
        title.textColor = Palette.textPrimary
        title.lineBreakMode = .byTruncatingTail

        let subtitle = UILabel()
        subtitle.text = account.visibleName
        subtitle.font = TextStyle.paragraph
        subtitle.textColor = Palette.textSecondary
        subtitle.lineBreakMode = .byTruncatingTail

        let column = UIStackView(arrangedSubviews: [title, subtitle])
        column.axis = .vertical
        column.spacing = 2
        column.setContentHuggingPriority(.defaultLow, for: .horizontal)

        let row = UIStackView(arrangedSubviews: [avatar, column])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.isUserInteractionEnabled = false
        if isActive {
            row.addArrangedSubview(UIImageView(image: UIImage(systemName: "checkmark")))
        }
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: 42),
            avatar.heightAnchor.constraint(equalToConstant: 42),
            row.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -10)
        ])

        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
        addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(held(_:))))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func tapped() { onTap() }

    @objc private func held(_ recognizer: UILongPressGestureRecognizer) {
        guard recognizer.state == .began else { return }
        onHold()
    }
}

/// The account switcher: list, add, and per-account actions, matching the
/// Android `AccountSheet`. The sheet presentation supplies the grabber and
/// drag-to-dismiss; alerts handle rename and removal.
final class AccountsSheetViewController: UIViewController {
    private enum Mode {
        case list
        case add
        case actions(MemosAccount)
    }

    private let controller: MemosTimelineController
    private var state: MemosAppState
    private var mode: Mode = .list
    private var observation: IosObservation?

    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private lazy var addItem = UIBarButtonItem(
        image: UIImage(systemName: "plus"),
        primaryAction: UIAction { [weak self] _ in self?.setMode(.add) }
    )

    init(controller: MemosTimelineController, state: MemosAppState) {
        self.controller = controller
        self.state = state
        super.init(nibName: nil, bundle: nil)
        title = "Accounts"
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink

        contentStack.axis = .vertical
        contentStack.spacing = 4
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        view.addSubview(scrollView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 12),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 20),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -20),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20),
            contentStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -40)
        ])

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            guard let self else { return }
            self.state = state
            self.render()
            if state.activeAccount == nil { self.dismiss(animated: true) }
        }
        render()
    }

    deinit {
        observation?.cancel()
    }

    private func setMode(_ mode: Mode) {
        self.mode = mode
        // Opening the actions asks the instance for its version once; render() runs on
        // every state change, so the request cannot live there.
        if case .actions(let account) = mode {
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.refreshInstanceVersion(accountId: account.id)
            }
        }
        render()
    }

    private func render() {
        contentStack.arrangedSubviews.forEach {
            contentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        switch mode {
        case .list:
            title = "Accounts"
            navigationItem.rightBarButtonItem = addItem
            navigationItem.leftBarButtonItem = nil
            renderList()
        case .add:
            title = "Add account"
            navigationItem.rightBarButtonItem = nil
            navigationItem.leftBarButtonItem = backItem()
            renderAdd()
        case .actions(let account):
            title = account.siteTitle
            navigationItem.rightBarButtonItem = nil
            navigationItem.leftBarButtonItem = backItem()
            renderActions(account)
        }
    }

    private func backItem() -> UIBarButtonItem {
        UIBarButtonItem(
            image: UIImage(systemName: "chevron.backward"),
            primaryAction: UIAction { [weak self] _ in self?.setMode(.list) }
        )
    }

    private func renderList() {
        for account in state.orderedAccounts {
            contentStack.addArrangedSubview(AccountRowView(
                account: account,
                isActive: account.id == state.activeAccountId,
                controller: controller,
                onTap: { [weak self] in
                    guard let self else { return }
                    Task { [weak self] in
                        guard let self else { return }
                        try? await self.controller.selectAccount(accountId: account.id)
                    }
                    self.dismiss(animated: true)
                },
                onHold: { [weak self] in self?.setMode(.actions(account)) }
            ))
        }

        let hint = UILabel()
        hint.text = "Hold an account to pin, rename, or remove it."
        hint.font = TextStyle.footnote1
        hint.textColor = Palette.textSecondary
        hint.numberOfLines = 0
        contentStack.addArrangedSubview(hint)
        contentStack.setCustomSpacing(12, after: hint)

        var doneConfiguration = UIButton.Configuration.filled()
        doneConfiguration.title = "Done"
        doneConfiguration.baseBackgroundColor = Palette.accent
        doneConfiguration.cornerStyle = .large
        let done = UIButton(type: .system)
        done.configuration = doneConfiguration
        done.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        contentStack.addArrangedSubview(done)
    }

    private func renderAdd() {
        let intro = UILabel()
        intro.text = "Add another Memos instance to switch between accounts."
        intro.font = TextStyle.paragraph
        intro.textColor = Palette.textSecondary
        intro.numberOfLines = 0

        let instance = UITextField.memo(memoPlaceholder: "memos.example.com")
        let username = UITextField.memo(memoPlaceholder: "Username")
        let password = UITextField.memo(memoPlaceholder: "Password", secure: true)

        let errorLabel = UILabel()
        errorLabel.font = TextStyle.paragraph
        errorLabel.textColor = Palette.danger
        errorLabel.numberOfLines = 0
        errorLabel.isHidden = true

        var cancelConfiguration = UIButton.Configuration.plain()
        cancelConfiguration.title = "Cancel"
        let cancel = UIButton(type: .system)
        cancel.configuration = cancelConfiguration
        cancel.addAction(UIAction { [weak self] _ in self?.setMode(.list) }, for: .touchUpInside)

        var addConfiguration = UIButton.Configuration.filled()
        addConfiguration.title = "Add"
        addConfiguration.baseBackgroundColor = Palette.accent
        addConfiguration.cornerStyle = .large
        let add = UIButton(type: .system)
        add.configuration = addConfiguration
        add.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            let instanceValue = instance.trimmedText
            let userValue = username.trimmedText
            let passwordValue = password.text ?? ""
            guard !instanceValue.isEmpty, !userValue.isEmpty, !passwordValue.isEmpty else {
                errorLabel.text = "Instance, username and password are required."
                errorLabel.isHidden = false
                return
            }
            add.isEnabled = false
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.addAccount(
                    instanceUrl: instanceValue,
                    username: userValue,
                    password: passwordValue
                )
                add.isEnabled = true
                if self.state.error == nil {
                    self.setMode(.list)
                } else {
                    errorLabel.text = self.state.error
                    errorLabel.isHidden = false
                }
            }
        }, for: .touchUpInside)

        let buttons = UIStackView(arrangedSubviews: [cancel, add])
        buttons.axis = .horizontal
        buttons.spacing = 12

        for view in [intro, instance, username, password, errorLabel, buttons] {
            contentStack.addArrangedSubview(view)
        }
        contentStack.setCustomSpacing(14, after: errorLabel)
    }

    private func renderActions(_ account: MemosAccount) {
        // The mode holds the snapshot the sheet opened with; take the live copy so the
        // version row shows what the refresh above returns.
        let account = state.accounts.first { $0.id == account.id } ?? account

        let version = UILabel()
        version.text = account.instanceVersion.isEmpty
            ? "Memos version unknown"
            : "Memos \(account.instanceVersion)"
        version.font = TextStyle.footnote1
        version.textColor = Palette.textSecondary
        contentStack.addArrangedSubview(version)

        contentStack.addArrangedSubview(ActionRowView(
            icon: account.pinned ? "pin.slash" : "pin",
            title: account.pinned ? "Unpin" : "Pin to top"
        ) { [weak self] in
            guard let self else { return }
            let pinned = !account.pinned
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.setAccountPinned(accountId: account.id, pinned: pinned)
            }
            self.setMode(.list)
        })
        contentStack.addArrangedSubview(ActionRowView(icon: "pencil", title: "Rename") { [weak self] in
            guard let self else { return }
            self.setMode(.list)
            self.presentRename(account)
        })
        contentStack.addArrangedSubview(ActionRowView(icon: "trash", title: "Remove account", destructive: true) { [weak self] in
            guard let self else { return }
            self.setMode(.list)
            self.presentRemove(account)
        })
    }

    private func presentRename(_ account: MemosAccount) {
        let alert = UIAlertController(title: "Rename account", message: account.instanceUrl, preferredStyle: .alert)
        alert.addTextField { $0.text = account.visibleName }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Save", style: .default) { [weak self] _ in
            guard let self else { return }
            let name = alert.textFields?.first?.trimmedText ?? ""
            guard !name.isEmpty else { return }
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.renameAccount(accountId: account.id, displayName: name)
            }
        })
        present(alert, animated: true)
    }

    private func presentRemove(_ account: MemosAccount) {
        let alert = UIAlertController(
            title: "Remove account?",
            message: "\(account.siteTitle) · \(account.visibleName) will be removed from this device. Memos stored on the server are not deleted.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Remove", style: .destructive) { [weak self] _ in
            guard let self else { return }
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.removeAccount(accountId: account.id)
            }
        })
        present(alert, animated: true)
    }
}

/// A tappable action row with an SF Symbol, used by the account actions menu.
final class ActionRowView: UIButton {
    init(icon: String, title: String, destructive: Bool = false, onTap: @escaping () -> Void) {
        super.init(frame: .zero)
        var configuration = UIButton.Configuration.plain()
        configuration.title = title
        configuration.image = UIImage(systemName: icon)
        configuration.imagePadding = 14
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 14, leading: 10, bottom: 14, trailing: 10)
        configuration.baseForegroundColor = destructive ? Palette.danger : Palette.textPrimary
        self.configuration = configuration
        contentHorizontalAlignment = .leading
        addAction(UIAction { _ in onTap() }, for: .touchUpInside)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}
