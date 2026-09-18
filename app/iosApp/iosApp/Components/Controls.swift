import UIKit

final class LoadingLineView: UIView {
    private let indicator = UIActivityIndicatorView(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        indicator.color = Palette.accent
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

/// A settings row whose value comes from a menu — the native stand-in for the
/// Android `SettingsOptionRow` plus its picker dialog.
final class MenuRow: UIView {
    private let titleLabel = UILabel()
    private let button = UIButton(type: .system)
    var onSelect: ((String) -> Void)?

    init(label: String) {
        super.init(frame: .zero)
        titleLabel.text = label
        titleLabel.font = TextStyle.paragraph
        titleLabel.textColor = Palette.textPrimary

        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: "chevron.up.chevron.down")
        configuration.imagePlacement = .trailing
        configuration.imagePadding = 6
        configuration.contentInsets = .zero
        configuration.baseForegroundColor = Palette.accent
        button.configuration = configuration
        button.showsMenuAsPrimaryAction = true
        button.contentHorizontalAlignment = .trailing

        let row = UIStackView(arrangedSubviews: [titleLabel, button])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -10)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setOptions(_ options: [(value: String, label: String)], selected: String) {
        button.menu = UIMenu(
            title: titleLabel.text ?? "",
            options: .singleSelection,
            children: options.map { option in
                UIAction(title: option.label, state: option.value == selected ? .on : .off) { [weak self] _ in
                    self?.button.configuration?.title = option.label
                    self?.onSelect?(option.value)
                }
            }
        )
        button.configuration?.title = options.first { $0.value == selected }?.label ?? selected
    }
}
