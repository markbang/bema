import UIKit

/// The Android `WindowDialog`: a dimmed backdrop with a dark card. Content is
/// wrapped in a scroll view that shrinks to fit but never exceeds a share of the
/// screen.
class MemosDialogViewController: UIViewController {
    private let titleText: String
    private let maxHeightRatio: CGFloat
    private let card = UIView()
    private let scrollView = UIScrollView()

    init(title: String, maxHeightRatio: CGFloat = 0.8) {
        self.titleText = title
        self.maxHeightRatio = maxHeightRatio
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    /// Called from `viewDidLoad` by subclasses once their content is built.
    func setContent(_ content: UIView) {
        content.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            content.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            content.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            content.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            content.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor)
        ])
        // Grow with the content, but never past the cap; beyond that it scrolls.
        scrollView.heightAnchor.constraint(
            equalTo: content.heightAnchor
        ).withPriority(.defaultHigh).isActive = true
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(backdropTapped)))

        card.backgroundColor = Palette.inkElevated
        card.layer.cornerRadius = 20
        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = TextStyle.title4.weight(.bold)
        titleLabel.textColor = Palette.textPrimary
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(titleLabel)

        scrollView.showsVerticalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(scrollView)

        NSLayoutConstraint.activate([
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            card.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            // Keep the card clear of the keyboard, like Compose's imePadding.
            card.bottomAnchor.constraint(lessThanOrEqualTo: view.keyboardLayoutGuide.topAnchor, constant: -20),

            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),

            scrollView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 16),
            scrollView.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            scrollView.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            scrollView.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20),

            scrollView.heightAnchor.constraint(
                lessThanOrEqualTo: view.heightAnchor,
                multiplier: maxHeightRatio
            )
        ])
    }

    @objc private func backdropTapped(_ recognizer: UITapGestureRecognizer) {
        guard !card.frame.contains(recognizer.location(in: view)) else { return }
        dismiss(animated: true)
    }
}

extension NSLayoutConstraint {
    func withPriority(_ priority: UILayoutPriority) -> NSLayoutConstraint {
        self.priority = priority
        return self
    }
}

/// `SettingsSwitch` / `SettingsOptionRow` from the Android settings sheet.
final class SwitchRow: UIView {
    private let toggle = UISwitch()
    private let onChange: (Bool) -> Void

    init(label: String, isOn: Bool, onChange: @escaping (Bool) -> Void) {
        self.onChange = onChange
        super.init(frame: .zero)
        let labelView = UILabel()
        labelView.text = label
        labelView.font = TextStyle.paragraph
        labelView.textColor = Palette.textPrimary
        labelView.numberOfLines = 0

        toggle.isOn = isOn
        toggle.onTintColor = Palette.accent
        toggle.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.onChange(self.toggle.isOn)
        }, for: .valueChanged)

        let row = UIStackView(arrangedSubviews: [labelView, toggle])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class OptionRow: UIView {
    private let valueLabel = UILabel()
    private let onTap: () -> Void

    init(label: String, value: String, onTap: @escaping () -> Void) {
        self.onTap = onTap
        super.init(frame: .zero)
        let labelView = UILabel()
        labelView.text = label
        labelView.font = TextStyle.paragraph
        labelView.textColor = Palette.textPrimary

        valueLabel.text = value
        valueLabel.font = TextStyle.paragraph
        valueLabel.textColor = Palette.textSecondary
        valueLabel.textAlignment = .right

        let row = UIStackView(arrangedSubviews: [labelView, valueLabel])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.isUserInteractionEnabled = false
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -10)
        ])
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapped)))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setValue(_ value: String) {
        valueLabel.text = value
    }

    @objc private func tapped() { onTap() }
}

final class SectionLabel: UILabel {
    init(_ text: String) {
        super.init(frame: .zero)
        self.text = text
        font = TextStyle.footnote1.weight(.semibold)
        textColor = Palette.accent
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// A vertical stack with the 14pt rhythm the Compose sheets use.
func sheetStack(_ views: [UIView], spacing: CGFloat = 14) -> UIStackView {
    let stack = UIStackView(arrangedSubviews: views)
    stack.axis = .vertical
    stack.spacing = spacing
    return stack
}
