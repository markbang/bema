import SharedLogic
import UIKit

/// Shared plumbing for the two list tabs. The navigation bar and tab bar own the
/// insets now, so this only deals with the table and paging.
class ContentListViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    let tableView = UITableView(frame: .zero, style: .plain)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink
        tableView.backgroundColor = Palette.ink
        tableView.separatorStyle = .none
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 320
        tableView.keyboardDismissMode = .interactive
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(MemoTweetCell.self, forCellReuseIdentifier: MemoTweetCell.reuseIdentifier)
        tableView.register(LoadingCell.self, forCellReuseIdentifier: LoadingCell.reuseIdentifier)
        tableView.register(ErrorCell.self, forCellReuseIdentifier: ErrorCell.reuseIdentifier)
        tableView.register(NewerMemosCell.self, forCellReuseIdentifier: NewerMemosCell.reuseIdentifier)
        tableView.register(RepliesHeaderCell.self, forCellReuseIdentifier: RepliesHeaderCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    /// Subclasses override to page in more content.
    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {}

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 0 }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        tableView.dequeueReusableCell(withIdentifier: LoadingCell.reuseIdentifier, for: indexPath)
    }

    /// A centered message for the states that have no rows to show.
    func showPlaceholder(_ message: String, tint: UIColor = Palette.textSecondary) {
        let label = UILabel()
        label.text = message
        label.font = TextStyle.paragraph
        label.textColor = tint
        label.numberOfLines = 0
        label.textAlignment = .center
        let container = UIView()
        container.frame = tableView.bounds
        label.frame = container.bounds.insetBy(dx: 32, dy: 0)
        label.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        container.addSubview(label)
        tableView.backgroundView = container
    }

    func clearPlaceholder() {
        tableView.backgroundView = nil
    }
}

final class LoadingCell: UITableViewCell {
    static let reuseIdentifier = "LoadingCell"

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = Palette.ink
        selectionStyle = .none
        let line = LoadingLineView()
        line.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(line)
        NSLayoutConstraint.activate([
            line.topAnchor.constraint(equalTo: contentView.topAnchor),
            line.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            line.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            line.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class ErrorCell: UITableViewCell {
    static let reuseIdentifier = "ErrorCell"
    private let label = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = Palette.ink
        selectionStyle = .none
        label.numberOfLines = 0
        label.font = TextStyle.paragraph
        label.textColor = Palette.danger
        label.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            label.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 18),
            label.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -18),
            label.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -14)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(message: String) {
        label.text = message
    }
}

/// "N replies" divider shown above the reply list on the detail screen.
final class RepliesHeaderCell: UITableViewCell {
    static let reuseIdentifier = "RepliesHeaderCell"
    private let label = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = Palette.ink
        selectionStyle = .none
        label.font = TextStyle.footnote1
        label.textColor = Palette.textSecondary
        label.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 12),
            label.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(lessThanOrEqualTo: contentView.trailingAnchor, constant: -16),
            label.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -12)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func configure(count: Int) {
        label.text = "\(count) \(count == 1 ? "reply" : "replies")"
    }
}

/// Shown when a silent revalidation found memos newer than the visible list.
final class NewerMemosCell: UITableViewCell {
    static let reuseIdentifier = "NewerMemosCell"
    private let button = UIButton(type: .system)
    private var tapAction: UIAction?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = Palette.ink
        selectionStyle = .none
        var configuration = UIButton.Configuration.filled()
        configuration.title = "New memos"
        configuration.image = UIImage(systemName: "arrow.clockwise")
        configuration.imagePadding = 6
        configuration.baseBackgroundColor = Palette.accent
        configuration.cornerStyle = .capsule
        button.configuration = configuration
        button.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(button)
        NSLayoutConstraint.activate([
            button.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            button.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
            button.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setAction(_ action: @escaping () -> Void) {
        // Cells are reused, so the previous action has to go.
        if let tapAction { button.removeAction(tapAction, for: .touchUpInside) }
        let tap = UIAction { _ in action() }
        button.addAction(tap, for: .touchUpInside)
        tapAction = tap
    }
}
