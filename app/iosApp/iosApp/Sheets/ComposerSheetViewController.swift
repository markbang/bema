import PhotosUI
import UIKit

/// The "New memo" dialog: markdown editor with a Write/Preview switch, the
/// visibility picker, attachments, then Cancel/Post. Mirrors `ComposerDialog`.
final class ComposerSheetViewController: MemosDialogViewController, PHPickerViewControllerDelegate {
    private struct Draft {
        let filename: String
        let data: Data
        let type: String
        let image: UIImage?
    }

    private let controller: MemosTimelineController
    private let composer = MarkdownComposerView(
        placeholder: "Write your memo in Markdown",
        editorHeight: 140...420
    )
    private let visibilityChips: [ChipButton]
    private let attachmentsScroll = UIScrollView()
    private let attachmentsStack = UIStackView()
    private let cancelButton = TextActionButton(title: "Cancel")
    private let postButton = TextActionButton(title: "Post", primary: true)
    private let buttonsRow = UIStackView()

    private var drafts: [Draft] = []
    private var visibilityIndex = 0
    private var observation: IosObservation?
    private var isPublishing = false

    init(controller: MemosTimelineController) {
        self.controller = controller
        visibilityChips = publishVisibilityLabels.enumerated().map { index, label in
            ChipButton(title: label, selected: index == 0) { }
        }
        super.init(title: "New memo", maxHeightRatio: 0.85)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        setContent(buildContent())
        observeKeyboard()
        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            guard let self else { return }
            self.isPublishing = state.isPublishing
            self.updatePostButton()
        }
    }

    deinit {
        observation?.cancel()
    }

    private func buildContent() -> UIView {
        composer.onTextChange = { [weak self] _ in self?.updatePostButton() }

        for (index, chip) in visibilityChips.enumerated() {
            chip.addAction(UIAction { [weak self] _ in self?.selectVisibility(index) }, for: .touchUpInside)
        }
        let visibilityRow = UIStackView(arrangedSubviews: visibilityChips)
        visibilityRow.axis = .horizontal
        visibilityRow.spacing = 8

        attachmentsStack.axis = .horizontal
        attachmentsStack.spacing = 8
        attachmentsStack.translatesAutoresizingMaskIntoConstraints = false
        attachmentsScroll.showsHorizontalScrollIndicator = false
        attachmentsScroll.translatesAutoresizingMaskIntoConstraints = false
        attachmentsScroll.isHidden = true
        attachmentsScroll.addSubview(attachmentsStack)
        NSLayoutConstraint.activate([
            attachmentsStack.topAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.topAnchor),
            attachmentsStack.leadingAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.leadingAnchor),
            attachmentsStack.trailingAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.trailingAnchor),
            attachmentsStack.bottomAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.bottomAnchor),
            attachmentsStack.heightAnchor.constraint(equalTo: attachmentsScroll.frameLayoutGuide.heightAnchor),
            attachmentsScroll.heightAnchor.constraint(equalToConstant: 78)
        ])

        let addIcon = UIImageView(image: MiuixIcons.image(.photos, size: 24, color: Palette.accent))
        let addLabel = UILabel()
        addLabel.text = "Add attachments"
        addLabel.font = TextStyle.paragraph
        addLabel.textColor = Palette.accent
        let addRow = UIStackView(arrangedSubviews: [addIcon, addLabel])
        addRow.axis = .horizontal
        addRow.spacing = 8
        addRow.alignment = .center
        addRow.isLayoutMarginsRelativeArrangement = true
        addRow.layoutMargins = UIEdgeInsets(top: 8, left: 0, bottom: 8, right: 0)
        addRow.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(addAttachments)))

        cancelButton.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        postButton.addAction(UIAction { [weak self] _ in self?.post() }, for: .touchUpInside)
        buttonsRow.axis = .horizontal
        buttonsRow.distribution = .fillEqually
        buttonsRow.spacing = 12
        buttonsRow.addArrangedSubview(cancelButton)
        buttonsRow.addArrangedSubview(postButton)

        let stack = sheetStack(
            [composer, visibilityRow, attachmentsScroll, addRow, buttonsRow],
            spacing: 12
        )
        updatePostButton()
        return stack
    }

    private func selectVisibility(_ index: Int) {
        visibilityIndex = index
        for (position, chip) in visibilityChips.enumerated() {
            chip.setSelected(position == index)
        }
    }

    private func updatePostButton() {
        let hasContent = !composer.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        postButton.isEnabled = (hasContent || !drafts.isEmpty) && !isPublishing
        postButton.setTitleText(isPublishing ? "Posting" : "Post")
    }

    private func observeKeyboard() {
        // Compose hides the action row while the keyboard is up; the card already
        // lifts above the keyboard via the dialog's layout guide.
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.buttonsRow.isHidden = true
        }
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main
        ) { [weak self] _ in
            self?.buttonsRow.isHidden = false
        }
    }

    @objc private func addAttachments() {
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 9
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        drafts.removeAll()
        attachmentsStack.arrangedSubviews.forEach {
            attachmentsStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        for result in results {
            result.itemProvider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
                guard let image = object as? UIImage, let data = image.jpegData(compressionQuality: 0.88) else { return }
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.drafts.append(Draft(
                        filename: "photo-\(self.drafts.count).jpg",
                        data: data,
                        type: "image/jpeg",
                        image: image
                    ))
                    self.attachmentsStack.addArrangedSubview(self.thumbnail(for: image))
                    self.attachmentsScroll.isHidden = false
                    self.updatePostButton()
                }
            }
        }
    }

    private func thumbnail(for image: UIImage) -> UIView {
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.layer.cornerRadius = 10
        imageView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(imageView)

        let remove = UIButton(type: .custom)
        remove.setTitle("×", for: .normal)
        remove.setTitleColor(.white, for: .normal)
        remove.titleLabel?.font = TextStyle.body1.weight(.bold)
        remove.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        remove.layer.cornerRadius = 12
        remove.translatesAutoresizingMaskIntoConstraints = false
        remove.addAction(UIAction { [weak self, weak container] _ in
            guard let self, let container else { return }
            if let index = self.attachmentsStack.arrangedSubviews.firstIndex(of: container) {
                self.attachmentsStack.removeArrangedSubview(container)
                container.removeFromSuperview()
                if index < self.drafts.count { self.drafts.remove(at: index) }
                self.attachmentsScroll.isHidden = self.drafts.isEmpty
                self.updatePostButton()
            }
        }, for: .touchUpInside)
        container.addSubview(remove)

        NSLayoutConstraint.activate([
            container.widthAnchor.constraint(equalToConstant: 78),
            container.heightAnchor.constraint(equalToConstant: 78),
            imageView.topAnchor.constraint(equalTo: container.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            remove.widthAnchor.constraint(equalToConstant: 24),
            remove.heightAnchor.constraint(equalToConstant: 24),
            remove.topAnchor.constraint(equalTo: container.topAnchor),
            remove.trailingAnchor.constraint(equalTo: container.trailingAnchor)
        ])
        return container
    }

    private func post() {
        let content = composer.text
        let attachments = drafts.map {
            PendingAttachment(filename: $0.filename, content: kotlinBytes($0.data), type: $0.type)
        }
        let visibility = publishVisibilities[visibilityIndex]
        postButton.isEnabled = false
        Task { [weak self] in
            guard let self else { return }
            try? await self.controller.publish(
                content: content,
                visibility: visibility,
                pendingAttachments: attachments
            )
            self.dismiss(animated: true)
        }
    }
}
