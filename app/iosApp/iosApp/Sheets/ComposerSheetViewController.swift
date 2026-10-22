import PhotosUI
import SharedLogic
import UIKit
import UniformTypeIdentifiers

/// The "New memo" sheet: markdown editor with a Write/Preview switch, the
/// visibility picker, attachments, and Cancel/Post in the navigation bar.
final class ComposerSheetViewController: UIViewController, PHPickerViewControllerDelegate {
    private struct Part {
        let filename: String
        let data: Data
        let type: String
        let motion: MotionMedia?
    }

    private struct Draft {
        let parts: [Part]
        let thumbnail: UIImage
    }

    private let controller: MemosTimelineController
    private let composer = MarkdownComposerView(
        placeholder: "Write your memo in Markdown",
        editorHeight: 140...420
    )
    private let visibility = UISegmentedControl(items: publishVisibilityLabels)
    private let attachmentsScroll = UIScrollView()
    private let attachmentsStack = UIStackView()
    private let progressLabel = UILabel()
    private let progressView = UIProgressView(progressViewStyle: .default)
    private let postButton = UIBarButtonItem(title: "Post", style: .done, target: nil, action: nil)

    private var drafts: [Draft] = []
    private var observation: IosObservation?
    private var isPublishing = false

    init(controller: MemosTimelineController) {
        self.controller = controller
        super.init(nibName: nil, bundle: nil)
        title = "New memo"
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Palette.ink
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            systemItem: .cancel,
            primaryAction: UIAction { [weak self] _ in self?.dismiss(animated: true) }
        )
        postButton.primaryAction = UIAction { [weak self] _ in self?.post() }
        postButton.tintColor = Palette.accent
        navigationItem.rightBarButtonItem = postButton

        let scrollView = UIScrollView()
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        composer.onTextChange = { [weak self] _ in self?.updatePostButton() }
        visibility.selectedSegmentIndex = 0
        visibility.selectedSegmentTintColor = Palette.accent

        attachmentsStack.axis = .horizontal
        attachmentsStack.spacing = 8
        attachmentsStack.translatesAutoresizingMaskIntoConstraints = false
        attachmentsScroll.showsHorizontalScrollIndicator = false
        attachmentsScroll.translatesAutoresizingMaskIntoConstraints = false
        attachmentsScroll.isHidden = true
        attachmentsScroll.addSubview(attachmentsStack)

        var add = UIButton.Configuration.plain()
        add.title = "Add attachments"
        add.image = UIImage(systemName: "photo")
        add.imagePadding = 8
        add.baseForegroundColor = Palette.accent
        let addButton = UIButton(type: .system)
        addButton.configuration = add
        addButton.contentHorizontalAlignment = .leading
        addButton.addAction(UIAction { [weak self] _ in self?.addAttachments() }, for: .touchUpInside)

        progressLabel.font = TextStyle.footnote1
        progressLabel.textColor = Palette.textSecondary
        progressLabel.numberOfLines = 1
        progressLabel.isHidden = true
        progressView.progressTintColor = Palette.accent
        progressView.trackTintColor = Palette.inkLine
        progressView.isHidden = true

        let stack = UIStackView(arrangedSubviews: [
            composer, visibility, attachmentsScroll, progressLabel, progressView, addButton
        ])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -16),
            stack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -32),

            attachmentsStack.topAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.topAnchor),
            attachmentsStack.leadingAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.leadingAnchor),
            attachmentsStack.trailingAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.trailingAnchor),
            attachmentsStack.bottomAnchor.constraint(equalTo: attachmentsScroll.contentLayoutGuide.bottomAnchor),
            attachmentsStack.heightAnchor.constraint(equalTo: attachmentsScroll.frameLayoutGuide.heightAnchor),
            attachmentsScroll.heightAnchor.constraint(equalToConstant: 78)
        ])

        observation = IosInterop.shared.observeState(flow: controller.state) { [weak self] state in
            guard let self else { return }
            self.isPublishing = state.isPublishing
            self.applyUploadProgress(state.uploadProgress)
            self.updatePostButton()
        }
        updatePostButton()
    }

    deinit {
        observation?.cancel()
    }

    private func applyUploadProgress(_ progress: UploadProgress?) {
        guard let progress else {
            progressLabel.isHidden = true
            progressView.isHidden = true
            return
        }
        progressLabel.isHidden = false
        progressView.isHidden = false
        progressLabel.text = "Uploading \(progress.filename)"
        progressView.setProgress(Float(progress.fraction), animated: true)
    }

    private func updatePostButton() {
        let hasContent = !composer.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        postButton.isEnabled = (hasContent || !drafts.isEmpty) && !isPublishing
        postButton.title = isPublishing ? "Posting" : "Post"
        navigationItem.leftBarButtonItem?.isEnabled = !isPublishing
        isModalInPresentation = isPublishing
        navigationController?.isModalInPresentation = isPublishing
        composer.isUserInteractionEnabled = !isPublishing
        visibility.isEnabled = !isPublishing
        attachmentsStack.isUserInteractionEnabled = !isPublishing
    }

    private func addAttachments() {
        guard !isPublishing else { return }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 9
        configuration.preferredAssetRepresentationMode = .current
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
        attachmentsScroll.isHidden = true
        for result in results {
            loadDraft(from: result) { [weak self] draft in
                guard let self, let draft else { return }
                self.drafts.append(draft)
                self.attachmentsStack.addArrangedSubview(self.thumbnail(for: draft.thumbnail))
                self.attachmentsScroll.isHidden = false
                self.updatePostButton()
            }
        }
    }

    private func loadDraft(from result: PHPickerResult, completion: @escaping (Draft?) -> Void) {
        let provider = result.itemProvider
        let live = provider.hasItemConformingToTypeIdentifier(UTType.livePhoto.identifier)
        func finish(_ draft: Draft?) {
            DispatchQueue.main.async { completion(draft) }
        }
        guard live else {
            provider.loadObject(ofClass: UIImage.self) { object, _ in
                guard let image = object as? UIImage,
                      let data = image.jpegData(compressionQuality: 0.88) else {
                    finish(nil)
                    return
                }
                finish(Draft(
                    parts: [Part(filename: "photo.jpg", data: data, type: "image/jpeg", motion: nil)],
                    thumbnail: image
                ))
            }
            return
        }
        loadFile(provider, type: .image) { imageData, imageType, imageName in
            guard let imageData, let image = UIImage(data: imageData) else {
                finish(nil)
                return
            }
            loadFile(provider, type: .movie) { movieData, movieType, movieName in
                guard let movieData else {
                    finish(Draft(
                        parts: [Part(filename: imageName, data: imageData, type: imageType, motion: nil)],
                        thumbnail: image
                    ))
                    return
                }
                let group = UUID().uuidString
                let parts = [
                    Part(
                        filename: imageName,
                        data: imageData,
                        type: imageType,
                        motion: MotionMedia(
                            family: .appleLivePhoto,
                            role: .still,
                            groupId: group,
                            presentationTimestampUs: 0,
                            hasEmbeddedVideo: false
                        )
                    ),
                    Part(
                        filename: movieName,
                        data: movieData,
                        type: movieType,
                        motion: MotionMedia(
                            family: .appleLivePhoto,
                            role: .video,
                            groupId: group,
                            presentationTimestampUs: 0,
                            hasEmbeddedVideo: false
                        )
                    )
                ]
                finish(Draft(parts: parts, thumbnail: image))
            }
        }
    }

    private func loadFile(
        _ provider: NSItemProvider,
        type: UTType,
        completion: @escaping (Data?, String, String) -> Void
    ) {
        let identifier = type.identifier
        guard provider.hasItemConformingToTypeIdentifier(identifier) else {
            completion(nil, "", "")
            return
        }
        provider.loadFileRepresentation(forTypeIdentifier: identifier) { url, _ in
            guard let url, let data = try? Data(contentsOf: url) else {
                completion(nil, "", "")
                return
            }
            let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                ?? type.preferredMIMEType
                ?? "application/octet-stream"
            completion(data, mime, url.lastPathComponent)
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

        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: "xmark")
        configuration.baseBackgroundColor = UIColor.black.withAlphaComponent(0.6)
        configuration.cornerStyle = .capsule
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 4, bottom: 4, trailing: 4)
        let remove = UIButton(type: .system)
        remove.configuration = configuration
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
            remove.topAnchor.constraint(equalTo: container.topAnchor, constant: 2),
            remove.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -2)
        ])
        return container
    }

    private func post() {
        guard !isPublishing else { return }
        let content = composer.text
        let attachments = drafts.flatMap { $0.parts }.map {
            PendingAttachment(
                filename: $0.filename,
                content: kotlinBytes($0.data),
                type: $0.type,
                motionMedia: $0.motion
            )
        }
        let index = max(visibility.selectedSegmentIndex, 0)
        let selected = index < publishVisibilities.count ? publishVisibilities[index] : .private_
        isPublishing = true
        updatePostButton()
        Task { [weak self] in
            guard let self else { return }
            do {
                try await self.controller.publish(
                    content: content,
                    visibility: selected,
                    pendingAttachments: attachments
                )
                self.dismiss(animated: true)
            } catch {
                self.isPublishing = false
                self.updatePostButton()
                presentError(error.localizedDescription, from: self)
            }
        }
    }
}
