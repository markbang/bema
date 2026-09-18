import UIKit

/// Bridges to the shared module and the handful of helpers every screen needs.

/// A Kotlin `ByteArray` is not an `NSData`; copy through the framework's iOS
/// bridge instead of reading it element by element through interop.
func kotlinBytes(_ data: Data) -> KotlinByteArray {
    IosInterop.shared.byteArray(data: data as NSData)
}

func image(from bytes: KotlinByteArray?) -> UIImage? {
    guard let bytes else { return nil }
    return UIImage(data: IosInterop.shared.nsData(bytes: bytes) as Data)
}

/// `users/lin` -> `lin`. Memos stores the creator as a resource name.
func username(of creator: String) -> String {
    creator.split(separator: "/").last.map(String.init) ?? ""
}

/// The like reaction, defined once in `sharedLogic`.
let heartReaction = MemoPresentationKt.HEART_REACTION

/// `2026-09-16T12:10:00Z` -> `12:10`, formatted by `sharedLogic` so both
/// platforms agree.
func formattedTime(_ memo: Memo) -> String {
    MemoPresentationKt.formatMemoTime(instant: memo.createTime)
}

/// Compose order is Private / Workspace / Public; Kotlin enum entries import
/// lowercased, and two of them collide with Swift keywords.
let publishVisibilities: [Visibility] = [.`private`, .protected, .`public`]

let publishVisibilityLabels = ["Private", "Workspace", "Public"]

func presentError(_ message: String, from viewController: UIViewController) {
    let alert = UIAlertController(title: "Memos", message: message, preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default))
    (viewController.presentedViewController ?? viewController).present(alert, animated: true)
}

/// Stands in for the Android `Toast` that confirms a copied link.
func showToast(_ message: String) {
    guard let window = UIApplication.shared.connectedScenes
        .compactMap({ $0 as? UIWindowScene })
        .flatMap(\.windows)
        .first(where: \.isKeyWindow)
    else { return }

    let label = UILabel()
    label.text = message
    label.font = TextStyle.body2
    label.textColor = Palette.textPrimary
    label.backgroundColor = Palette.inkElevated
    label.textAlignment = .center
    label.layer.cornerRadius = 10
    label.clipsToBounds = true
    label.alpha = 0
    label.translatesAutoresizingMaskIntoConstraints = false
    window.addSubview(label)
    NSLayoutConstraint.activate([
        label.centerXAnchor.constraint(equalTo: window.centerXAnchor),
        label.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor, constant: -90),
        label.heightAnchor.constraint(equalToConstant: 36),
        label.widthAnchor.constraint(equalToConstant: label.intrinsicContentSize.width + 32)
    ])
    UIView.animate(withDuration: 0.2) {
        label.alpha = 1
    } completion: { _ in
        UIView.animate(withDuration: 0.25, delay: 1.4) {
            label.alpha = 0
        } completion: { _ in
            label.removeFromSuperview()
        }
    }
}

extension UIView {
    var parentViewController: UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController { return viewController }
            responder = current.next
        }
        return nil
    }
}

extension UIImageView {
    /// Avatars and site logos fall back to a coloured circle with an initial.
    func setAvatar(_ image: UIImage?, label: String) {
        let initial = label.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "M"
        if let image {
            self.image = image
            backgroundColor = Palette.avatarBackground
            return
        }
        self.image = nil
        backgroundColor = Palette.avatarBackground
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 96, height: 96))
        self.image = renderer.image { context in
            Palette.avatarBackground.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 96, height: 96))
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 44, weight: .bold),
                .foregroundColor: Palette.textPrimary
            ]
            let size = initial.size(withAttributes: attributes)
            initial.draw(
                at: CGPoint(x: 48 - size.width / 2, y: 48 - size.height / 2),
                withAttributes: attributes
            )
        }
    }
}
