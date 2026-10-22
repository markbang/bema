import SharedLogic
import UIKit

/// One page in the image viewer: pinch to zoom, double tap to toggle, long
/// press for the image actions.
final class ZoomingImagePage: UIScrollView, UIScrollViewDelegate {
    let imageView = UIImageView()
    var onLongPress: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        minimumZoomScale = 1
        maximumZoomScale = 5
        bouncesZoom = true
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        delegate = self

        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = true
        addSubview(imageView)

        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(doubleTapped(_:)))
        doubleTap.numberOfTapsRequired = 2
        addGestureRecognizer(doubleTap)

        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(longPressed(_:)))
        longPress.minimumPressDuration = 0.45
        addGestureRecognizer(longPress)
    }

    convenience init() {
        self.init(frame: .zero)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        if zoomScale == 1 {
            imageView.frame = bounds
            contentSize = bounds.size
        }
        panGestureRecognizer.isEnabled = zoomScale > 1
        centerImage()
    }

    func setImage(_ image: UIImage?) {
        imageView.image = image
        setZoomScale(1, animated: false)
        setNeedsLayout()
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        panGestureRecognizer.isEnabled = zoomScale > 1
        centerImage()
    }

    private func centerImage() {
        let horizontal = max((bounds.width - contentSize.width) / 2, 0)
        let vertical = max((bounds.height - contentSize.height) / 2, 0)
        contentInset = UIEdgeInsets(top: vertical, left: horizontal, bottom: vertical, right: horizontal)
    }

    @objc private func doubleTapped(_ gesture: UITapGestureRecognizer) {
        guard zoomScale <= 1 else {
            setZoomScale(1, animated: true)
            return
        }
        let point = gesture.location(in: imageView)
        let scale = min(maximumZoomScale, 2.5)
        let size = CGSize(width: bounds.width / scale, height: bounds.height / scale)
        zoom(
            to: CGRect(
                x: point.x - size.width / 2,
                y: point.y - size.height / 2,
                width: size.width,
                height: size.height
            ),
            animated: true
        )
    }

    @objc private func longPressed(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        onLongPress?()
    }
}

/// Full-screen pager for a memo's images: zoomable pages, a position counter,
/// and image actions from a long press.
final class ImageViewerViewController: UIViewController, UIScrollViewDelegate {
    private let controller: MemosTimelineController
    private let memo: Memo
    private let images: [Attachment]
    private let startIndex: Int

    private let scroll = UIScrollView()
    private let closeButton = UIButton(type: .custom)
    private let counter = UILabel()
    private var pages: [ZoomingImagePage] = []
    private var didPosition = false

    init(controller: MemosTimelineController, memo: Memo, startIndex: Int) {
        self.controller = controller
        self.memo = memo
        self.images = memo.attachments.filter(\.isImage)
        self.startIndex = min(max(startIndex, 0), max(memo.attachments.filter(\.isImage).count - 1, 0))
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        guard !images.isEmpty else { return }

        scroll.isPagingEnabled = true
        scroll.showsHorizontalScrollIndicator = false
        scroll.delegate = self
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)

        for attachment in images {
            let page = ZoomingImagePage()
            page.onLongPress = { [weak self, weak page] in
                guard let self, let image = page?.imageView.image else { return }
                self.showActions(image: image, source: page)
            }
            scroll.addSubview(page)
            pages.append(page)
            Task { [weak page] in
                let bytes = try? await controller.attachmentBytes(attachment: attachment, thumbnail: false)
                guard !Task.isCancelled else { return }
                page?.setImage(decodedImage(from: bytes))
            }
        }

        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.accessibilityLabel = "Close image"
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        view.addSubview(closeButton)

        counter.font = TextStyle.footnote1
        counter.textColor = UIColor.white.withAlphaComponent(0.8)
        counter.translatesAutoresizingMaskIntoConstraints = false
        counter.isHidden = images.count <= 1
        view.addSubview(counter)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
            closeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            closeButton.widthAnchor.constraint(equalToConstant: 40),
            closeButton.heightAnchor.constraint(equalToConstant: 40),

            counter.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            counter.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16)
        ])
        updateCounter()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let width = view.bounds.width
        let height = view.bounds.height
        guard width > 0, !pages.isEmpty else { return }
        scroll.contentSize = CGSize(width: width * CGFloat(pages.count), height: height)
        for (index, page) in pages.enumerated() {
            page.frame = CGRect(x: width * CGFloat(index), y: 0, width: width, height: height)
        }
        if !didPosition {
            didPosition = true
            scroll.setContentOffset(CGPoint(x: width * CGFloat(startIndex), y: 0), animated: false)
            updateCounter()
        }
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        updateCounter()
    }

    private func updateCounter() {
        guard !images.isEmpty, view.bounds.width > 0 else { return }
        let page = Int((scroll.contentOffset.x / view.bounds.width).rounded())
        counter.text = "\(min(page, images.count - 1) + 1) / \(images.count)"
    }

    private func showActions(image: UIImage, source: UIView?) {
        let sheet = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Save to Photos", style: .default) { [weak self] _ in
            self?.save(image: image)
        })
        sheet.addAction(UIAlertAction(title: "Copy image", style: .default) { _ in
            UIPasteboard.general.image = image
            showToast("Image copied")
        })
        sheet.addAction(UIAlertAction(title: "Share image", style: .default) { [weak self] _ in
            self?.share(image: image, source: source)
        })
        sheet.addAction(UIAlertAction(title: "Copy memo link", style: .default) { [weak self] _ in
            guard let self else { return }
            UIPasteboard.general.string = self.controller.memoUrl(memo: self.memo)
            showToast("Link copied")
        })
        sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = source ?? view
            popover.sourceRect = source?.bounds ?? view.bounds
        }
        present(sheet, animated: true)
    }

    private func save(image: UIImage) {
        UIImageWriteToSavedPhotosAlbum(
            image,
            self,
            #selector(image(_:didFinishSavingWithError:contextInfo:)),
            nil
        )
    }

    @objc private func image(
        _ image: UIImage,
        didFinishSavingWithError error: Error?,
        contextInfo: UnsafeMutableRawPointer?
    ) {
        DispatchQueue.main.async {
            showToast(error == nil ? "Saved to Photos" : "Couldn't save image")
        }
    }

    private func share(image: UIImage, source: UIView?) {
        let share = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        if let popover = share.popoverPresentationController {
            popover.sourceView = source ?? view
            popover.sourceRect = source?.bounds ?? view.bounds
        }
        present(share, animated: true)
    }
}
