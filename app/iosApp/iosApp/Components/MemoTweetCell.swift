import SharedLogic
import UIKit

/// The actions a memo row hands back to the enclosing screen. Copy-link and
/// share stay inside the cell, where the Android `MemoTweet` also implements
/// them.
struct MemoActions {
    var open: (() -> Void)?
    var reply: (() -> Void)?
    var react: (() -> Void)?
    var openImage: ((Int) -> Void)?
}

/// One image in the media rail: cropped to fill, rounded, and tappable.
final class MediaPageView: UIControl {
    private let imageView = UIImageView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        layer.cornerRadius = Metrics.mediaCornerRadius
        backgroundColor = Palette.inkElevated
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = false
        imageView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setImage(_ image: UIImage?) {
        imageView.image = image
    }
}

/// One memo, laid out the way the Android `MemoTweet` is: avatar, byline,
/// markdown body, tags, a horizontal media rail, then the action row.
final class MemoTweetCell: UITableViewCell {
    static let reuseIdentifier = "MemoTweetCell"

    private let avatarView = AvatarImageView()
    private let contentStack = UIStackView()
    private let openArea = UIStackView()
    private let bylineLabel = UILabel()
    private let bodyLabel = UILabel()
    private let tagsLabel = UILabel()
    private let mediaScroll = UIScrollView()
    private let mediaStack = UIStackView()
    private let actionsStack = UIStackView()
    private let separator = UIView()

    private let replyButton = UIButton(type: .system)
    private let linkButton = UIButton(type: .system)
    private let likeButton = UIButton(type: .system)
    private let shareButton = UIButton(type: .system)

    private var imageTasks: [Task<Void, Never>] = []
    private var actions = MemoActions()
    private var currentLink: String?
    private var currentSnippet = ""

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = Palette.ink
        contentView.backgroundColor = Palette.ink
        selectionStyle = .none
        configureViews()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func prepareForReuse() {
        super.prepareForReuse()
        imageTasks.forEach { $0.cancel() }
        imageTasks.removeAll()
        avatarView.image = nil
        mediaStack.arrangedSubviews.forEach {
            mediaStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        actions = MemoActions()
    }

    private func configureViews() {
        avatarView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            avatarView.widthAnchor.constraint(equalToConstant: Metrics.avatarSize),
            avatarView.heightAnchor.constraint(equalToConstant: Metrics.avatarSize)
        ])

        contentStack.axis = .vertical
        contentStack.spacing = 8
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        bylineLabel.numberOfLines = 1
        bylineLabel.lineBreakMode = .byTruncatingTail

        bodyLabel.numberOfLines = 0
        bodyLabel.lineBreakMode = .byWordWrapping

        tagsLabel.numberOfLines = 0
        tagsLabel.font = TextStyle.paragraph
        tagsLabel.textColor = Palette.accent

        mediaScroll.showsHorizontalScrollIndicator = false
        mediaScroll.translatesAutoresizingMaskIntoConstraints = false
        mediaStack.axis = .horizontal
        mediaStack.spacing = 8
        mediaStack.translatesAutoresizingMaskIntoConstraints = false
        mediaScroll.addSubview(mediaStack)
        NSLayoutConstraint.activate([
            mediaStack.leadingAnchor.constraint(equalTo: mediaScroll.leadingAnchor),
            mediaStack.trailingAnchor.constraint(equalTo: mediaScroll.trailingAnchor),
            mediaStack.topAnchor.constraint(equalTo: mediaScroll.topAnchor),
            mediaStack.bottomAnchor.constraint(equalTo: mediaScroll.bottomAnchor),
            mediaStack.heightAnchor.constraint(equalToConstant: Metrics.mediaHeight),
            mediaScroll.heightAnchor.constraint(equalToConstant: Metrics.mediaHeight)
        ])

        actionsStack.axis = .horizontal
        actionsStack.distribution = .equalSpacing
        actionsStack.alignment = .center

        separator.backgroundColor = Palette.inkLine
        separator.translatesAutoresizingMaskIntoConstraints = false

        replyButton.accessibilityLabel = "Reply"
        linkButton.accessibilityLabel = "Copy link"
        likeButton.accessibilityLabel = "Like"
        shareButton.accessibilityLabel = "Share"
        [replyButton, linkButton, likeButton, shareButton].forEach(actionsStack.addArrangedSubview)

        // Compose makes only the byline/body/tags block tappable, not the media
        // rail or the action row.
        openArea.axis = .vertical
        openArea.spacing = 8
        openArea.isUserInteractionEnabled = true
        openArea.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(openTapped)))

        let root = UIStackView(arrangedSubviews: [avatarView, contentStack])
        root.axis = .horizontal
        root.alignment = .top
        root.spacing = 12
        root.translatesAutoresizingMaskIntoConstraints = false

        [bylineLabel, bodyLabel, tagsLabel].forEach(openArea.addArrangedSubview)
        openArea.setCustomSpacing(5, after: bylineLabel)
        [openArea, mediaScroll, actionsStack].forEach(contentStack.addArrangedSubview)

        contentView.addSubview(root)
        contentView.addSubview(separator)
        NSLayoutConstraint.activate([
            root.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            root.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            root.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            root.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -14),
            separator.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            separator.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            separator.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            separator.heightAnchor.constraint(equalToConstant: 1)
        ])

        replyButton.addTarget(self, action: #selector(replyTapped), for: .touchUpInside)
        linkButton.addTarget(self, action: #selector(copyLinkTapped), for: .touchUpInside)
        likeButton.addTarget(self, action: #selector(reactTapped), for: .touchUpInside)
        shareButton.addTarget(self, action: #selector(shareTapped), for: .touchUpInside)
    }

    func configure(
        memo: Memo,
        user: User?,
        controller: MemosTimelineController,
        liked: Bool,
        showsActions: Bool = true,
        actions: MemoActions
    ) {
        self.actions = actions

        let fallback = username(of: memo.creator)
        let name = user?.visibleName ?? (fallback.isEmpty ? "Memos" : fallback)
        let handle = user?.username ?? fallback
        bylineLabel.attributedText = byline(name: name, handle: handle, time: formattedTime(memo))

        bodyLabel.attributedText = Markdown.attributed(memo.content)

        currentLink = controller.memoUrl(memo: memo)
        currentSnippet = memo.content

        tagsLabel.text = memo.tags.map { "#\($0)" }.joined(separator: "  ")
        tagsLabel.isHidden = memo.tags.isEmpty

        let images = memo.attachments.filter(\.isImage)
        mediaScroll.isHidden = images.isEmpty
        for (index, attachment) in images.enumerated() {
            let page = MediaPageView()
            page.translatesAutoresizingMaskIntoConstraints = false
            page.tag = index
            page.addTarget(self, action: #selector(imageTapped(_:)), for: .touchUpInside)
            NSLayoutConstraint.activate([
                page.widthAnchor.constraint(equalToConstant: Metrics.mediaWidth),
                page.heightAnchor.constraint(equalToConstant: Metrics.mediaHeight)
            ])
            mediaStack.addArrangedSubview(page)
            imageTasks.append(Task { [weak page] in
                let bytes = try? await controller.attachmentBytes(attachment: attachment, thumbnail: true)
                guard !Task.isCancelled else { return }
                page?.setImage(decodedImage(from: bytes))
            })
        }

        actionsStack.isHidden = !showsActions
        let reactionCount = memo.reactions.filter { $0.reactionType == heartReaction }.count
        let comments = memo.relations.filter { $0.type == .comment }.count
        update(replyButton, icon: .reply, count: comments, tint: Palette.textSecondary)
        update(linkButton, icon: .link, count: 0, tint: Palette.textSecondary)
        update(likeButton, icon: liked ? .favoritesFilled : .favorites, count: reactionCount, tint: liked ? Palette.accent : Palette.textSecondary)
        update(shareButton, icon: .share, count: 0, tint: Palette.textSecondary)

        if let user {
            imageTasks.append(Task { [weak self] in
                let bytes = try? await controller.avatarBytes(user: user)
                guard !Task.isCancelled else { return }
                self?.avatarView.setAvatar(decodedImage(from: bytes), label: name)
            })
        } else {
            avatarView.setAvatar(nil, label: name)
        }
    }

    private func byline(name: String, handle: String, time: String) -> NSAttributedString {
        let result = NSMutableAttributedString()
        result.append(NSAttributedString(string: name, attributes: [
            .font: TextStyle.paragraph.weight(.bold),
            .foregroundColor: Palette.textPrimary
        ]))
        result.append(NSAttributedString(string: "  @\(handle)  · \(time)", attributes: [
            .font: TextStyle.paragraph,
            .foregroundColor: Palette.textSecondary
        ]))
        return result
    }

    private func update(_ button: UIButton, icon: MiuixIcon, count: Int, tint: UIColor) {
        var configuration = UIButton.Configuration.plain()
        configuration.image = MiuixIcons.image(icon, size: 20, color: tint)
        configuration.imagePadding = 5
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 8, bottom: 6, trailing: 8)
        if count > 0 {
            configuration.attributedTitle = AttributedString(String(count), attributes: AttributeContainer([
                .font: TextStyle.footnote2,
                .foregroundColor: tint
            ]))
        }
        button.configuration = configuration
    }

    @objc private func openTapped() { actions.open?() }
    @objc private func replyTapped() { actions.reply?() }
    @objc private func reactTapped() { actions.react?() }

    @objc private func copyLinkTapped() {
        guard let link = currentLink, !link.isEmpty else { return }
        UIPasteboard.general.string = link
        showToast("Link copied")
    }

    @objc private func shareTapped() {
        guard let link = currentLink, let presenter = parentViewController else { return }
        let snippet = currentSnippet.trimmingCharacters(in: .whitespacesAndNewlines)
        let text = snippet.isEmpty ? link : "\(String(snippet.prefix(180)))\n\(link)"
        let controller = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = self
        controller.popoverPresentationController?.sourceRect = bounds
        presenter.present(controller, animated: true)
    }

    @objc private func imageTapped(_ sender: UIButton) {
        actions.openImage?(sender.tag)
    }
}
