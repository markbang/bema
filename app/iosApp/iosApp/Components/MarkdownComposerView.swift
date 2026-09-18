import UIKit

/// The dark markdown editing surface: Compose's `MarkdownEditor`.
final class MarkdownTextView: UITextView {
    var onChange: ((String) -> Void)?

    private let placeholderLabel = UILabel()

    init(placeholder: String) {
        super.init(frame: .zero, textContainer: nil)
        backgroundColor = Palette.editorSurface
        layer.cornerRadius = Metrics.mediaCornerRadius
        textColor = Palette.textPrimary
        font = TextStyle.paragraph
        tintColor = Palette.accent
        textContainerInset = UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)
        textContainer.lineFragmentPadding = 0

        placeholderLabel.text = placeholder
        placeholderLabel.font = TextStyle.paragraph
        placeholderLabel.textColor = Palette.textSecondary
        placeholderLabel.numberOfLines = 0
        placeholderLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(placeholderLabel)
        NSLayoutConstraint.activate([
            placeholderLabel.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            placeholderLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            placeholderLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setText(_ value: String) {
        text = value
        placeholderLabel.isHidden = !value.isEmpty
        onChange?(value)
    }

    /// Wraps the selection (or inserts the pair) the way `insertMarkdown` does.
    func wrapSelection(with prefix: String, suffix: String) {
        let ns = text as NSString
        let range = selectedRange
        let selected = ns.substring(with: range)
        let replacement = prefix + selected + suffix
        text = ns.replacingCharacters(in: range, with: replacement)
        let cursor = selected.isEmpty
            ? range.location + (prefix as NSString).length
            : range.location + (replacement as NSString).length
        selectedRange = NSRange(location: cursor, length: 0)
        notifyChange()
    }

    /// Inserts a block marker at the start of the current line.
    func prefixCurrentLine(with prefix: String) {
        let ns = text as NSString
        let selectionStart = selectedRange.location
        let lineStart = ns.lineRange(for: NSRange(location: selectionStart, length: 0)).location
        text = ns.replacingCharacters(in: NSRange(location: lineStart, length: 0), with: prefix)
        selectedRange = NSRange(location: selectionStart + (prefix as NSString).length, length: 0)
        notifyChange()
    }

    private func notifyChange() {
        placeholderLabel.isHidden = !(text ?? "").isEmpty
        onChange?(text ?? "")
    }
}

/// The markdown shortcut row: B, I, `</>`, link, heading, list.
final class MarkdownToolbar: UIView {
    enum Command {
        case bold, italic, code, link, heading, list
    }

    var onCommand: ((Command) -> Void)?

    private static let entries: [(String, String, Command)] = [
        ("B", "Bold", .bold),
        ("I", "Italic", .italic),
        ("</>", "Code", .code),
        ("🔗", "Link", .link),
        ("#", "Heading", .heading),
        ("-", "List", .list)
    ]

    override init(frame: CGRect) {
        super.init(frame: frame)
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 6
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
        for (symbol, label, command) in Self.entries {
            let button = ChipButton(title: symbol, selected: false) { [weak self] in
                self?.onCommand?(command)
            }
            button.accessibilityLabel = label
            button.titleLabel?.font = TextStyle.body1.weight(.medium)
            button.widthAnchor.constraint(greaterThanOrEqualToConstant: 36).isActive = true
            button.heightAnchor.constraint(equalToConstant: 36).isActive = true
            row.addArrangedSubview(button)
        }
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// Write/Preview toggle plus the editing surface. Shared by the post composer
/// and the reply composer, like `MarkdownModeToggle` + editor + toolbar.
final class MarkdownComposerView: UIView {
    var onTextChange: ((String) -> Void)?

    var text: String { editor.text ?? "" }

    private let writeChip: ChipButton
    private let previewChip: ChipButton
    private let editor: MarkdownTextView
    private let previewScroll = UIScrollView()
    private let previewLabel = UILabel()
    private let toolbar = MarkdownToolbar()

    private var isPreview = false

    init(placeholder: String, editorHeight: ClosedRange<CGFloat>) {
        writeChip = ChipButton(title: "Write", selected: true) { }
        previewChip = ChipButton(title: "Preview", selected: false) { }
        editor = MarkdownTextView(placeholder: placeholder)
        super.init(frame: .zero)

        writeChip.addAction(UIAction { [weak self] _ in self?.setPreview(false) }, for: .touchUpInside)
        previewChip.addAction(UIAction { [weak self] _ in self?.setPreview(true) }, for: .touchUpInside)

        editor.translatesAutoresizingMaskIntoConstraints = false
        editor.isScrollEnabled = editorHeight.lowerBound == editorHeight.upperBound
        editor.onChange = { [weak self] value in self?.onTextChange?(value) }

        previewLabel.numberOfLines = 0
        previewLabel.translatesAutoresizingMaskIntoConstraints = false
        previewScroll.backgroundColor = Palette.editorSurface
        previewScroll.layer.cornerRadius = Metrics.mediaCornerRadius
        previewScroll.translatesAutoresizingMaskIntoConstraints = false
        previewScroll.isHidden = true
        previewScroll.addSubview(previewLabel)

        let toggle = UIStackView(arrangedSubviews: [writeChip, previewChip])
        toggle.axis = .horizontal
        toggle.spacing = 8

        toolbar.onCommand = { [weak self] command in self?.apply(command) }

        let stack = UIStackView(arrangedSubviews: [toggle, editor, previewScroll, toolbar])
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),

            editor.heightAnchor.constraint(greaterThanOrEqualToConstant: editorHeight.lowerBound),
            editor.heightAnchor.constraint(lessThanOrEqualToConstant: editorHeight.upperBound),
            previewScroll.heightAnchor.constraint(greaterThanOrEqualToConstant: editorHeight.lowerBound),
            previewScroll.heightAnchor.constraint(lessThanOrEqualToConstant: editorHeight.upperBound),

            previewLabel.topAnchor.constraint(equalTo: previewScroll.contentLayoutGuide.topAnchor, constant: 16),
            previewLabel.leadingAnchor.constraint(equalTo: previewScroll.contentLayoutGuide.leadingAnchor, constant: 16),
            previewLabel.trailingAnchor.constraint(equalTo: previewScroll.contentLayoutGuide.trailingAnchor, constant: -16),
            previewLabel.bottomAnchor.constraint(equalTo: previewScroll.contentLayoutGuide.bottomAnchor, constant: -16),
            previewLabel.widthAnchor.constraint(equalTo: previewScroll.frameLayoutGuide.widthAnchor, constant: -32)
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setPreview(_ preview: Bool) {
        isPreview = preview
        writeChip.setSelected(!preview)
        previewChip.setSelected(preview)
        editor.isHidden = preview
        previewScroll.isHidden = !preview
        if preview {
            let content = editor.text ?? ""
            if content.isEmpty {
                previewLabel.attributedText = NSAttributedString(string: "Nothing to preview", attributes: [
                    .font: TextStyle.paragraph,
                    .foregroundColor: Palette.textSecondary
                ])
            } else {
                previewLabel.attributedText = Markdown.attributed(content)
            }
        }
    }

    /// Mirrors `insertMarkdown` / `insertLinePrefix`.
    private func apply(_ command: MarkdownToolbar.Command) {
        switch command {
        case .bold: editor.wrapSelection(with: "**", suffix: "**")
        case .italic: editor.wrapSelection(with: "*", suffix: "*")
        case .code: editor.wrapSelection(with: "`", suffix: "`")
        case .link: editor.wrapSelection(with: "[", suffix: "]()")
        case .heading: editor.prefixCurrentLine(with: "## ")
        case .list: editor.prefixCurrentLine(with: "- ")
        }
    }
}
