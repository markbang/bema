import SharedLogic
import UIKit

/// The timeline header: account avatar (long-press opens the account sheet),
/// site title, then search and refresh.
final class TimelineHeaderView: UIView {
    var onAccounts: (() -> Void)?
    var onSearch: (() -> Void)?
    var onRefresh: (() -> Void)?

    private let avatar = AvatarImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let searchButton = UIButton(type: .custom)
    private let refreshButton = UIButton(type: .custom)
    private var avatarTask: Task<Void, Never>?
    private var loadedAccountId: String?

    override init(frame: CGRect) {
        super.init(frame: frame)
        avatar.translatesAutoresizingMaskIntoConstraints = false
        avatar.isUserInteractionEnabled = true
        avatar.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(avatarHeld)))
        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: Metrics.headerAvatarSize),
            avatar.heightAnchor.constraint(equalToConstant: Metrics.headerAvatarSize)
        ])

        titleLabel.font = TextStyle.title3.weight(.bold)
        titleLabel.textColor = Palette.textPrimary
        titleLabel.lineBreakMode = .byTruncatingTail
        subtitleLabel.font = TextStyle.footnote1
        subtitleLabel.textColor = Palette.textSecondary
        subtitleLabel.lineBreakMode = .byTruncatingTail

        let column = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        column.axis = .vertical
        column.spacing = 0
        column.setContentHuggingPriority(.defaultLow, for: .horizontal)

        style(searchButton, icon: .search, label: "Open search")
        style(refreshButton, icon: .refresh, label: "Refresh")
        searchButton.addAction(UIAction { [weak self] _ in self?.onSearch?() }, for: .touchUpInside)
        refreshButton.addAction(UIAction { [weak self] _ in self?.onRefresh?() }, for: .touchUpInside)

        let row = UIStackView(arrangedSubviews: [avatar, column, searchButton, refreshButton])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func style(_ button: UIButton, icon: MiuixIcon, label: String) {
        button.setImage(MiuixIcons.image(icon, size: 24, color: Palette.textPrimary), for: .normal)
        button.accessibilityLabel = label
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 40),
            button.heightAnchor.constraint(equalToConstant: 40)
        ])
    }

    @objc private func avatarHeld(_ recognizer: UILongPressGestureRecognizer) {
        guard recognizer.state == .began else { return }
        onAccounts?()
    }

    func update(account: MemosAccount, controller: MemosTimelineController) {
        titleLabel.text = account.siteTitle
        subtitleLabel.text = account.visibleName
        guard loadedAccountId != account.id else { return }
        loadedAccountId = account.id
        avatarTask?.cancel()
        avatarTask = Task { [weak self] in
            let bytes = try? await controller.accountAvatarBytes(account: account)
            guard !Task.isCancelled else { return }
            self?.avatar.setAvatar(decodedImage(from: bytes), label: account.visibleName)
        }
    }
}

final class SearchHeaderView: UIView {
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        titleLabel.text = "Search"
        titleLabel.font = TextStyle.title3.weight(.bold)
        titleLabel.textColor = Palette.textPrimary
        subtitleLabel.font = TextStyle.footnote1
        subtitleLabel.textColor = Palette.textSecondary

        let column = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        column.axis = .vertical
        column.spacing = 0
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(siteTitle: String) {
        subtitleLabel.text = "in \(siteTitle)"
    }
}
