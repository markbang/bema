import SharedLogic
import UIKit

/// Colours and type copied from the Android UI's design tokens
/// (`MemosApp.kt` palette plus miuix `TextStyles`).
///
/// Every colour is dynamic: it resolves against the window's interface style,
/// which `AppDelegate` derives from the shared `ThemeMode` preference. That keeps
/// call sites reading `Palette.ink` unchanged while the whole app follows the theme.
enum Palette {
    static let ink = adaptiveColor(light: 0xF5F6F8, dark: 0x050505)
    static let inkElevated = adaptiveColor(light: 0xFFFFFF, dark: 0x101010)
    static let inkLine = adaptiveColor(light: 0xE2E4E9, dark: 0x272727)
    static let editorSurface = adaptiveColor(light: 0xEDEFF3, dark: 0x4A4A4A)
    static let avatarBackground = adaptiveColor(light: 0xDCE2E9, dark: 0x202B35)
    static let textPrimary = adaptiveColor(light: 0x16181D, dark: 0xF2F2F2)
    static let textSecondary = adaptiveColor(light: 0x63676E, dark: 0x8C8C8C)
    static let accent = adaptiveColor(light: 0x0A7BC0, dark: 0x1D9BF0)
    static let danger = adaptiveColor(light: 0xC53B3B, dark: 0xFF6B6B)
}

private func adaptiveColor(light: UInt32, dark: UInt32) -> UIColor {
    UIColor { traits in
        UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
    }
}

extension ThemeMode {
    /// How the window should resolve the dynamic palette; `system` leaves the
    /// choice to the OS so live changes to the device setting keep working.
    var interfaceStyle: UIUserInterfaceStyle {
        switch ThemePreferenceKt.themeModeValue(mode: self) {
        case "light": return .light
        case "dark": return .dark
        default: return .unspecified
        }
    }
}

/// miuix text styles are font size only; the weight is a call-site choice there,
/// so `weight(_:)` below is how call sites express it here.
enum TextStyle {
    static let title1 = UIFont.systemFont(ofSize: 32)
    static let title2 = UIFont.systemFont(ofSize: 24)
    static let title3 = UIFont.systemFont(ofSize: 20)
    static let headline1 = UIFont.systemFont(ofSize: 17)
    static let paragraph = UIFont.systemFont(ofSize: 17)
    static let body1 = UIFont.systemFont(ofSize: 16)
    static let body2 = UIFont.systemFont(ofSize: 14)
    static let subtitle = UIFont.systemFont(ofSize: 14, weight: .bold)
    static let footnote1 = UIFont.systemFont(ofSize: 13)
    static let footnote2 = UIFont.systemFont(ofSize: 11)
}

enum Metrics {
    static let avatarSize: CGFloat = 46
    static let mediaWidth: CGFloat = 260
    static let mediaHeight: CGFloat = 210
    static let mediaCornerRadius: CGFloat = 16
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
