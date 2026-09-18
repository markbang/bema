import UIKit

/// A tappable settings/action row: icon, label, optional destructive tint.
final class TapRowView: UIView {
    private let onTap: () -> Void

    init(icon: MiuixIcon, title: String, destructive: Bool = false, onTap: @escaping () -> Void) {
        self.onTap = onTap
        super.init(frame: .zero)
        let color = destructive ? Palette.danger : Palette.textPrimary
        let imageView = UIImageView(image: MiuixIcons.image(icon, size: 22, color: color))
        imageView.translatesAutoresizingMaskIntoConstraints = false
        let label = UILabel()
        label.text = title
        label.font = TextStyle.paragraph.weight(.medium)
        label.textColor = color

        let row = UIStackView(arrangedSubviews: [imageView, label])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 14
        row.isUserInteractionEnabled = false
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 14),
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10),
            row.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -10),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -14),
            imageView.widthAnchor.constraint(equalToConstant: 22),
            imageView.heightAnchor.constraint(equalToConstant: 22)
        ])
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func tapped() { onTap() }
}

/// One row of the account list: avatar, site title, account name, and a check
/// when it is the active one.
final class AccountRowView: UIView {
    let account: MemosAccount
    private let onTap: () -> Void
    private let onHold: () -> Void

    init(account: MemosAccount, isActive: Bool, onTap: @escaping () -> Void, onHold: @escaping () -> Void) {
        self.account = account
        self.onTap = onTap
        self.onHold = onHold
        super.init(frame: .zero)

        let avatar = AvatarImageView()
        avatar.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: 42),
            avatar.heightAnchor.constraint(equalToConstant: 42)
        ])
        avatar.setAvatar(nil, label: account.visibleName)

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
            row.addArrangedSubview(UIImageView(image: MiuixIcons.image(.ok, size: 20, color: Palette.accent)))
        }
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10),
            row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -10),
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

/// The account switcher bottom sheet: list, add, and per-account actions,
/// matching the Android `AccountSheet`.
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

    private let card = UIView()
    private let titleLabel = UILabel()
    private let contentContainer = UIView()
    private let startAction = UIButton(type: .custom)
    private let endAction = UIButton(type: .custom)
    private var panOffset: CGFloat = 0

    init(controller: MemosTimelineController, state: MemosAppState) {
        self.controller = controller
        self.state = state
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(backdropTapped)))

        card.backgroundColor = Palette.inkElevated
        card.layer.cornerRadius = 20
        card.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)
        card.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(panned(_:))))

        titleLabel.font = TextStyle.title4.weight(.bold)
        titleLabel.textColor = Palette.textPrimary
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(titleLabel)

        configureAccessory(startAction, icon: .back, label: "Back to accounts")
        configureAccessory(endAction, icon: .add, label: "Add account")
        startAction.addAction(UIAction { [weak self] _ in self?.setMode(.list) }, for: .touchUpInside)
        endAction.addAction(UIAction { [weak self] _ in self?.setMode(.add) }, for: .touchUpInside)

        contentContainer.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(contentContainer)

        NSLayoutConstraint.activate([
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            card.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: startAction.trailingAnchor, constant: 8),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: endAction.leadingAnchor, constant: -8),

            startAction.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 12),
            startAction.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            endAction.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -12),
            endAction.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),

            contentContainer.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 14),
            contentContainer.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            contentContainer.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            contentContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -8)
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

    private func configureAccessory(_ button: UIButton, icon: MiuixIcon, label: String) {
        button.setImage(MiuixIcons.image(icon, size: 24, color: Palette.textPrimary), for: .normal)
        button.accessibilityLabel = label
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 36),
            button.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    private func setMode(_ mode: Mode) {
        self.mode = mode
        render()
    }

    private func render() {
        contentContainer.subviews.forEach { $0.removeFromSuperview() }
        switch mode {
        case .list:
            titleLabel.text = "Accounts"
            startAction.isHidden = true
            endAction.isHidden = false
            renderList()
        case .add:
            titleLabel.text = "Add account"
            startAction.isHidden = false
            endAction.isHidden = true
            renderAdd()
        case .actions(let account):
            titleLabel.text = account.siteTitle
            startAction.isHidden = false
            endAction.isHidden = true
            renderActions(account)
        }
    }

    private func renderList() {
        var rows: [UIView] = state.orderedAccounts.map { account in
            AccountRowView(
                account: account,
                isActive: account.id == state.activeAccountId,
                onTap: { [weak self] in
                    guard let self else { return }
                    Task { [weak self] in
                        guard let self else { return }
                        try? await self.controller.selectAccount(accountId: account.id)
                    }
                    self.dismiss(animated: true)
                },
                onHold: { [weak self] in self?.setMode(.actions(account)) }
            )
        }

        let hint = UILabel()
        hint.text = "Hold an account to pin, rename, or remove it."
        hint.font = TextStyle.footnote1
        hint.textColor = Palette.textSecondary
        hint.numberOfLines = 0
        rows.append(hint)

        let done = TextActionButton(title: "Done", primary: true)
        done.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        rows.append(done)

        install(sheetStack(rows, spacing: 4))
    }

    private func renderAdd() {
        let instance = DarkTextField(placeholder: "memos.example.com")
        let username = DarkTextField(placeholder: "Username")
        let password = DarkTextField(placeholder: "Password", secure: true)

        let intro = UILabel()
        intro.text = "Add another Memos instance to switch between accounts."
        intro.font = TextStyle.paragraph
        intro.textColor = Palette.textSecondary
        intro.numberOfLines = 0

        let errorLabel = UILabel()
        errorLabel.font = TextStyle.paragraph
        errorLabel.textColor = Palette.danger
        errorLabel.numberOfLines = 0
        errorLabel.isHidden = true

        let cancel = TextActionButton(title: "Cancel")
        cancel.addAction(UIAction { [weak self] _ in self?.setMode(.list) }, for: .touchUpInside)
        let add = TextActionButton(title: "Add", primary: true)
        add.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            let instanceValue = instance.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let userValue = username.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
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
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        install(sheetStack([intro, instance, username, password, errorLabel, buttons], spacing: 12))
    }

    private func renderActions(_ account: MemosAccount) {
        let pin = TapRowView(
            icon: account.pinned ? .unpin : .pin,
            title: account.pinned ? "Unpin" : "Pin to top"
        ) { [weak self] in
            guard let self else { return }
            let pinned = !account.pinned
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.setAccountPinned(accountId: account.id, pinned: pinned)
            }
            self.setMode(.list)
        }
        let rename = TapRowView(icon: .rename, title: "Rename") { [weak self] in
            guard let self else { return }
            self.setMode(.list)
            self.presentRename(account)
        }
        let remove = TapRowView(icon: .delete, title: "Remove account", destructive: true) { [weak self] in
            guard let self else { return }
            self.setMode(.list)
            self.presentRemove(account)
        }
        install(sheetStack([pin, rename, remove], spacing: 2))
    }

    private func install(_ stack: UIStackView) {
        stack.translatesAutoresizingMaskIntoConstraints = false
        contentContainer.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            stack.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor)
        ])
    }

    private func presentRename(_ account: MemosAccount) {
        let field = DarkTextField(placeholder: "Display name")
        field.text = account.visibleName

        let cancel = TextActionButton(title: "Cancel")
        let save = TextActionButton(title: "Save", primary: true)
        let buttons = UIStackView(arrangedSubviews: [cancel, save])
        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        let dialog = MemosDialogViewController(title: "Rename account")
        cancel.addAction(UIAction { [weak dialog] _ in dialog?.dismiss(animated: true) }, for: .touchUpInside)
        save.addAction(UIAction { [weak self, weak dialog] _ in
            guard let self else { return }
            let name = field.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !name.isEmpty else { return }
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.renameAccount(accountId: account.id, displayName: name)
            }
            dialog?.dismiss(animated: true)
        }, for: .touchUpInside)

        dialog.setContent(sheetStack([field, buttons], spacing: 14))
        present(dialog, animated: true)
    }

    private func presentRemove(_ account: MemosAccount) {
        let message = UILabel()
        message.text = "\(account.siteTitle) · \(account.visibleName) will be removed from this device. Memos stored on the server are not deleted."
        message.font = TextStyle.paragraph
        message.textColor = Palette.textSecondary
        message.numberOfLines = 0

        let cancel = TextActionButton(title: "Cancel")
        let remove = TextActionButton(title: "Remove", primary: true)
        let buttons = UIStackView(arrangedSubviews: [cancel, remove])
        buttons.axis = .horizontal
        buttons.distribution = .fillEqually
        buttons.spacing = 12

        let dialog = MemosDialogViewController(title: "Remove account?")
        cancel.addAction(UIAction { [weak dialog] _ in dialog?.dismiss(animated: true) }, for: .touchUpInside)
        remove.addAction(UIAction { [weak self, weak dialog] _ in
            guard let self else { return }
            Task { [weak self] in
                guard let self else { return }
                try? await self.controller.removeAccount(accountId: account.id)
            }
            dialog?.dismiss(animated: true)
        }, for: .touchUpInside)

        dialog.setContent(sheetStack([message, buttons], spacing: 14))
        present(dialog, animated: true)
    }

    @objc private func backdropTapped(_ recognizer: UITapGestureRecognizer) {
        guard !card.frame.contains(recognizer.location(in: view)) else { return }
        dismiss(animated: true)
    }

    @objc private func panned(_ recognizer: UIPanGestureRecognizer) {
        switch recognizer.state {
        case .changed:
            panOffset = max(0, recognizer.translation(in: view).y)
            card.transform = CGAffineTransform(translationX: 0, y: panOffset)
        case .ended, .cancelled:
            if panOffset > 120 {
                dismiss(animated: true)
            } else {
                UIView.animate(withDuration: 0.2) { self.card.transform = .identity }
            }
            panOffset = 0
        default:
            break
        }
    }
}
