import UIKit

/// The markdown shortcuts the toolbar offers.
enum MarkdownCommand {
    case bold, italic, code, link, heading, list
}

/// The dark markdown editing surface. Kept custom: it is part of the shared
/// design rather than platform chrome.
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

/// Write/Preview switch, editor and the markdown toolbar — the pieces Android
/// calls `MarkdownModeToggle`, `MarkdownEditor` and `MarkdownToolbar`.
final class MarkdownComposerView: UIView {
    var onTextChange: ((String) -> Void)?
    var text: String { editor.text ?? "" }

    private let modeControl = UISegmentedControl(items: ["Write", "Preview"])
    private let editor: MarkdownTextView
    private let previewScroll = UIScrollView()
    private let previewLabel = UILabel()
    private let toolbar = UIToolbar()

    init(placeholder: String, editorHeight: ClosedRange<CGFloat>) {
        editor = MarkdownTextView(placeholder: placeholder)
        super.init(frame: .zero)

        modeControl.selectedSegmentIndex = 0
        modeControl.addAction(UIAction { [weak self] _ in
            self?.setPreview(self?.modeControl.selectedSegmentIndex == 1)
        }, for: .valueChanged)

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

        toolbar.translatesAutoresizingMaskIntoConstraints = false
        toolbar.items = [
            barItem("B", label: "Bold", command: .bold),
            barItem("I", label: "Italic", command: .italic),
            barItem("</>", label: "Code", command: .code),
            barItem("link", label: "Link", command: .link, symbol: "link"),
            barItem("#", label: "Heading", command: .heading),
            barItem("-", label: "List", command: .list)
        ]

        let stack = UIStackView(arrangedSubviews: [modeControl, editor, previewScroll, toolbar])
        stack.axis = .vertical
        stack.spacing = 10
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

    func setText(_ value: String) {
        editor.setText(value)
    }

    private func barItem(_ title: String, label: String, command: MarkdownCommand, symbol: String? = nil) -> UIBarButtonItem {
        let item: UIBarButtonItem
        if let symbol {
            item = UIBarButtonItem(image: UIImage(systemName: symbol), style: .plain, target: nil, action: nil)
        } else {
            item = UIBarButtonItem(title: title, style: .plain, target: nil, action: nil)
            item.setTitleTextAttributes([.font: TextStyle.body1.weight(.semibold)], for: .normal)
        }
        item.accessibilityLabel = label
        item.primaryAction = UIAction { [weak self] _ in self?.apply(command) }
        return item
    }

    private func setPreview(_ preview: Bool) {
        modeControl.selectedSegmentIndex = preview ? 1 : 0
        editor.isHidden = preview
        previewScroll.isHidden = !preview
        guard preview else { return }
        let content = editor.text ?? ""
        previewLabel.attributedText = content.isEmpty
            ? NSAttributedString(string: "Nothing to preview", attributes: [
                .font: TextStyle.paragraph,
                .foregroundColor: Palette.textSecondary
            ])
            : Markdown.attributed(content)
    }

    /// Mirrors `insertMarkdown` / `insertLinePrefix`.
    private func apply(_ command: MarkdownCommand) {
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
