import SharedLogic
import UIKit

/// Full-screen pager for a memo's images, matching the Android `ImageViewer`:
/// black backdrop, position counter, tap the close button to leave.
final class ImageViewerViewController: UIViewController, UIScrollViewDelegate {
    private let controller: MemosTimelineController
    private let images: [Attachment]
    private let startIndex: Int

    private let scroll = UIScrollView()
    private let closeButton = UIButton(type: .custom)
    private let counter = UILabel()
    private var pages: [UIImageView] = []
    private var didPosition = false

    init(controller: MemosTimelineController, memo: Memo, startIndex: Int) {
        self.controller = controller
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
            let page = UIImageView()
            page.contentMode = .scaleAspectFit
            page.backgroundColor = .black
            scroll.addSubview(page)
            pages.append(page)
            Task { [weak page] in
                let bytes = try? await controller.attachmentBytes(attachment: attachment, thumbnail: false)
                guard !Task.isCancelled else { return }
                page?.image = decodedImage(from: bytes)
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
}
