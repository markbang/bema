import UIKit

/// The gradient header that overlays the list and slides away on scroll,
/// matching the Android `TopChrome`.
final class TopChromeView: UIView {
    private let gradientLayer: CAGradientLayer = {
        let layer = CAGradientLayer()
        layer.colors = [
            Palette.ink.withAlphaComponent(0.88).cgColor,
            Palette.ink.withAlphaComponent(0.42).cgColor,
            UIColor.clear.cgColor
        ]
        layer.locations = [0, 0.55, 1]
        layer.startPoint = CGPoint(x: 0.5, y: 0)
        layer.endPoint = CGPoint(x: 0.5, y: 1)
        return layer
    }()

    private let contentHolder = UIView()
    private var contentInsets: UIEdgeInsets = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = true
        layer.addSublayer(gradientLayer)
        contentHolder.translatesAutoresizingMaskIntoConstraints = false
        addSubview(contentHolder)
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
        contentHolder.frame = CGRect(
            x: 0,
            y: safeAreaInsets.top,
            width: bounds.width,
            height: max(0, bounds.height - safeAreaInsets.top)
        )
    }

    /// Lays `content` out below the status bar with the Compose header's padding.
    func install(_ content: UIView, insets: UIEdgeInsets) {
        contentHolder.subviews.forEach { $0.removeFromSuperview() }
        contentInsets = insets
        content.translatesAutoresizingMaskIntoConstraints = false
        contentHolder.addSubview(content)
        NSLayoutConstraint.activate([
            content.leadingAnchor.constraint(equalTo: contentHolder.leadingAnchor, constant: insets.left),
            content.trailingAnchor.constraint(equalTo: contentHolder.trailingAnchor, constant: -insets.right),
            content.topAnchor.constraint(equalTo: contentHolder.topAnchor, constant: insets.top),
            content.bottomAnchor.constraint(equalTo: contentHolder.bottomAnchor, constant: -insets.bottom)
        ])
        setNeedsLayout()
    }

    /// Status bar inset plus the content's own height, never below the 56pt floor
    /// the Compose layout uses.
    func fittingHeight(width: CGFloat) -> CGFloat {
        let floor = safeAreaInsets.top + Metrics.headerMinimumHeight
        guard let content = contentHolder.subviews.first else { return floor }
        let available = max(0, width - contentInsets.left - contentInsets.right)
        let contentHeight = content.systemLayoutSizeFitting(
            CGSize(width: available, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        ).height
        return max(safeAreaInsets.top + contentHeight + contentInsets.top + contentInsets.bottom, floor)
    }
}

/// Keeps the status bar legible once the chrome slides away.
final class StatusBarVeilView: UIView {
    private let gradientLayer = CAGradientLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        gradientLayer.colors = [
            Palette.ink.withAlphaComponent(0.78).cgColor,
            UIColor.clear.cgColor
        ]
        gradientLayer.startPoint = CGPoint(x: 0.5, y: 0)
        gradientLayer.endPoint = CGPoint(x: 0.5, y: 1)
        layer.addSublayer(gradientLayer)
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
    }
}

final class BottomNavView: UIView {
    var onSelect: ((Int) -> Void)?
    var onSettings: (() -> Void)?

    var selectedIndex: Int = 0 {
        didSet { updateSelection() }
    }

    private let stack = UIStackView()
    private var buttons: [UIButton] = []
    private let icons: [MiuixIcon] = [.home, .search, .settings]

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = Palette.ink.withAlphaComponent(0.92)
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.heightAnchor.constraint(equalToConstant: Metrics.bottomNavHeight)
        ])
        for (index, icon) in icons.enumerated() {
            let button = UIButton(type: .custom)
            button.setImage(MiuixIcons.image(icon, size: 24, color: Palette.textSecondary), for: .normal)
            button.tag = index
            button.addTarget(self, action: #selector(tapped(_:)), for: .touchUpInside)
            stack.addArrangedSubview(button)
            buttons.append(button)
        }
        updateSelection()
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func tapped(_ sender: UIButton) {
        if sender.tag == 2 {
            onSettings?()
        } else {
            onSelect?(sender.tag)
        }
    }

    private func updateSelection() {
        for (index, button) in buttons.enumerated() {
            let selected = index == selectedIndex
            let color = selected ? Palette.accent : Palette.textSecondary
            button.setImage(MiuixIcons.image(icons[index], size: 24, color: color), for: .normal)
        }
    }
}

final class LoadingLineView: UIView {
    private let indicator = UIActivityIndicatorView(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        indicator.color = Palette.accent
        indicator.transform = CGAffineTransform(scaleX: 1.3, y: 1.3)
        indicator.startAnimating()
        indicator.translatesAutoresizingMaskIntoConstraints = false
        addSubview(indicator)
        NSLayoutConstraint.activate([
            indicator.centerXAnchor.constraint(equalTo: centerXAnchor),
            indicator.topAnchor.constraint(equalTo: topAnchor, constant: 18),
            indicator.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -18)
        ])
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class AvatarImageView: UIImageView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        contentMode = .scaleAspectFill
        clipsToBounds = true
        backgroundColor = Palette.avatarBackground
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = bounds.height / 2
    }
}

// MARK: - Reusable controls

final class PrimaryButton: UIButton {
    override var isEnabled: Bool {
        didSet { alpha = isEnabled ? 1 : 0.4 }
    }

    init(title: String) {
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        setTitleColor(.white, for: .normal)
        titleLabel?.font = TextStyle.headline1.weight(.medium)
        backgroundColor = Palette.accent
        layer.cornerRadius = 24
        heightAnchor.constraint(equalToConstant: 48).isActive = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class TextActionButton: UIButton {
    init(title: String, primary: Bool = false) {
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        setTitleColor(primary ? Palette.accent : Palette.textPrimary, for: .normal)
        titleLabel?.font = TextStyle.headline1
        heightAnchor.constraint(equalToConstant: 44).isActive = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override var isEnabled: Bool {
        didSet { alpha = isEnabled ? 1 : 0.4 }
    }
}

/// The Compose `MarkdownModeButton`: a small rounded toggle used for the
/// Write/Preview switch and the visibility picker.
final class ChipButton: UIButton {
    private let onSelect: () -> Void

    init(title: String, selected: Bool, onSelect: @escaping () -> Void) {
        self.onSelect = onSelect
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        titleLabel?.font = TextStyle.body2.weight(.medium)
        contentEdgeInsets = UIEdgeInsets(top: 7, left: 12, bottom: 7, right: 12)
        layer.cornerRadius = 8
        setSelected(selected)
        addTarget(self, action: #selector(tapped), for: .touchUpInside)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    @objc private func tapped() { onSelect() }

    func setSelected(_ selected: Bool) {
        backgroundColor = selected ? Palette.accent : Palette.inkLine
        setTitleColor(selected ? .white : Palette.textSecondary, for: .normal)
    }
}

/// A dark miuix-style field. miuix floats the label above the field once there is
/// content; a placeholder gives the same two visible states without that motion.
final class DarkTextField: UITextField {
    private let leadingInset: CGFloat

    init(placeholder: String, secure: Bool = false, icon: MiuixIcon? = nil) {
        leadingInset = icon == nil ? 14 : 42
        super.init(frame: .zero)
        attributedPlaceholder = NSAttributedString(
            string: placeholder,
            attributes: [.foregroundColor: Palette.textSecondary]
        )
        textColor = Palette.textPrimary
        font = TextStyle.paragraph
        isSecureTextEntry = secure
        autocapitalizationType = .none
        autocorrectionType = .no
        backgroundColor = Palette.inkElevated
        layer.cornerRadius = Metrics.corner
        layer.borderWidth = 1
        layer.borderColor = Palette.inkLine.cgColor
        translatesAutoresizingMaskIntoConstraints = false
        heightAnchor.constraint(equalToConstant: 48).isActive = true

        if let icon {
            let iconView = UIImageView(image: MiuixIcons.image(icon, size: 20, color: Palette.textSecondary))
            iconView.translatesAutoresizingMaskIntoConstraints = false
            addSubview(iconView)
            NSLayoutConstraint.activate([
                iconView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 14),
                iconView.centerYAnchor.constraint(equalTo: centerYAnchor),
                iconView.widthAnchor.constraint(equalToConstant: 20),
                iconView.heightAnchor.constraint(equalToConstant: 20)
            ])
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func textRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: UIEdgeInsets(top: 0, left: leadingInset, bottom: 0, right: 14))
    }

    override func editingRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: UIEdgeInsets(top: 0, left: leadingInset, bottom: 0, right: 14))
    }

    override func placeholderRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: UIEdgeInsets(top: 0, left: leadingInset, bottom: 0, right: 14))
    }
}
