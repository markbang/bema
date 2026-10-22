import AVFoundation
import SharedLogic
import UIKit

/// Plays the video half of a Live Photo or an Android motion photo.
final class LivePhotoViewController: UIViewController {
    private let controller: MemosTimelineController
    private let memo: Memo
    private let attachment: Attachment

    private let spinner = UIActivityIndicatorView(style: .large)
    private let errorLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?
    private var loopObserver: NSObjectProtocol?

    init(controller: MemosTimelineController, memo: Memo, attachment: Attachment) {
        self.controller = controller
        self.memo = memo
        self.attachment = attachment
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        spinner.color = Palette.accent
        spinner.startAnimating()
        spinner.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(spinner)

        errorLabel.text = "Motion video unavailable"
        errorLabel.textColor = .white
        errorLabel.font = TextStyle.paragraph
        errorLabel.textAlignment = .center
        errorLabel.numberOfLines = 0
        errorLabel.isHidden = true
        errorLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(errorLabel)

        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: "xmark")
        configuration.baseBackgroundColor = UIColor.black.withAlphaComponent(0.55)
        configuration.cornerStyle = .capsule
        closeButton.configuration = configuration
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addAction(UIAction { [weak self] _ in self?.dismiss(animated: true) }, for: .touchUpInside)
        view.addSubview(closeButton)

        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            errorLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            errorLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),
            errorLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16)
        ])

        Task { [weak self] in
            guard let self else { return }
            let bytes = try? await self.controller.motionBytes(memo: self.memo, attachment: self.attachment)
            guard let bytes, bytes.size > 0 else {
                self.spinner.stopAnimating()
                self.errorLabel.isHidden = false
                return
            }
            let data = IosInterop.shared.nsData(bytes: bytes) as Data
            let safeName = self.attachment.name.replacingOccurrences(of: "/", with: "-")
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("bema-motion-\(safeName).mp4")
            guard (try? data.write(to: url)) != nil else {
                self.spinner.stopAnimating()
                self.errorLabel.isHidden = false
                return
            }
            self.play(url: url)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        playerLayer?.frame = view.bounds
    }

    deinit {
        if let loopObserver {
            NotificationCenter.default.removeObserver(loopObserver)
        }
        player?.pause()
    }

    private func play(url: URL) {
        spinner.stopAnimating()
        let player = AVPlayer(url: url)
        player.isMuted = true
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, at: 0)
        playerLayer = layer
        self.player = player

        loopObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: player.currentItem,
            queue: .main
        ) { [weak player] _ in
            player?.seek(to: .zero)
            player?.play()
        }
        player.play()
    }
}
