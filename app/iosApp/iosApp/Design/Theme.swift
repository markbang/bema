import UIKit

/// Colours and type copied from the Android UI's design tokens
/// (`MemosApp.kt` palette plus miuix `TextStyles`).
enum Palette {
    static let ink = UIColor(rgb: 0x050505)
    static let inkElevated = UIColor(rgb: 0x101010)
    static let inkLine = UIColor(rgb: 0x272727)
    static let editorSurface = UIColor(rgb: 0x4A4A4A)
    static let avatarBackground = UIColor(rgb: 0x202B35)
    static let textPrimary = UIColor(rgb: 0xF2F2F2)
    static let textSecondary = UIColor(rgb: 0x8C8C8C)
    static let accent = UIColor(rgb: 0x1D9BF0)
    static let danger = UIColor(rgb: 0xFF6B6B)
}

/// miuix text styles are font size only; the weight is a call-site choice there,
/// so `weight(_:)` below is how call sites express it here.
enum TextStyle {
    static let title1 = UIFont.systemFont(ofSize: 32)
    static let title2 = UIFont.systemFont(ofSize: 24)
    static let title3 = UIFont.systemFont(ofSize: 20)
    static let title4 = UIFont.systemFont(ofSize: 18)
    static let headline1 = UIFont.systemFont(ofSize: 17)
    static let paragraph = UIFont.systemFont(ofSize: 17)
    static let body1 = UIFont.systemFont(ofSize: 16)
    static let body2 = UIFont.systemFont(ofSize: 14)
    static let subtitle = UIFont.systemFont(ofSize: 14, weight: .bold)
    static let footnote1 = UIFont.systemFont(ofSize: 13)
    static let footnote2 = UIFont.systemFont(ofSize: 11)
}

enum Metrics {
    static let headerMinimumHeight: CGFloat = 56
    static let avatarSize: CGFloat = 46
    static let headerAvatarSize: CGFloat = 40
    static let bottomNavHeight: CGFloat = 56
    static let fabSize: CGFloat = 56
    static let mediaWidth: CGFloat = 260
    static let mediaHeight: CGFloat = 210
    static let mediaCornerRadius: CGFloat = 16
    static let corner: CGFloat = 12
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension UIFont {
    func weight(_ weight: UIFont.Weight) -> UIFont {
        UIFont.systemFont(ofSize: pointSize, weight: weight)
    }
}
