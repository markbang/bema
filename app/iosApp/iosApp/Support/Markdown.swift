import UIKit

/// Renders memo bodies the way the Android `MarkdownText` composable does:
/// line-level structure (`#`/`##`/`###`, `-`/`*` lists, `>` quotes) plus inline
/// `**bold**`, `` `code` ``, `[label](url)` and `*italic*`.
///
/// One attributed string rather than a stack of labels, so cells stay cheap to
/// reuse.
enum Markdown {

    static func attributed(_ markdown: String) -> NSAttributedString {
        let result = NSMutableAttributedString()
        let lines = markdown.components(separatedBy: "\n")

        for (index, line) in lines.enumerated() {
            let styled: (String, UIFont, UIColor, Bool)
            switch true {
            case line.isEmpty:
                styled = ("", TextStyle.paragraph, Palette.textPrimary, true)
            case line.hasPrefix("### "):
                styled = (String(line.dropFirst(4)), TextStyle.headline1.weight(.bold), Palette.textPrimary, false)
            case line.hasPrefix("## "):
                styled = (String(line.dropFirst(3)), TextStyle.title3.weight(.bold), Palette.textPrimary, false)
            case line.hasPrefix("# "):
                styled = (String(line.dropFirst(2)), TextStyle.title2.weight(.bold), Palette.textPrimary, false)
            case line.hasPrefix("- "), line.hasPrefix("* "):
                styled = ("• " + String(line.dropFirst(2)), TextStyle.paragraph, Palette.textPrimary, false)
            case line.hasPrefix("> "):
                styled = (String(line.dropFirst(2)), TextStyle.paragraph, Palette.textSecondary, true)
            default:
                styled = (line, TextStyle.paragraph, Palette.textPrimary, false)
            }

            let (text, font, color, italic) = styled
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font,
                .foregroundColor: color
            ]
            if italic { attributes[.font] = UIFont.italicSystemFont(ofSize: font.pointSize) }

            let body = text.isEmpty
                ? NSMutableAttributedString(string: "")
                : inline(text, base: attributes)

            // Blank lines are spacers in the Compose version; a short empty
            // paragraph is the same gap here. The last line contributes no
            // trailing spacing.
            let lineHeight = text.isEmpty ? 4 : (font.pointSize * 1.2).rounded()
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            paragraph.paragraphSpacing = index < lines.count - 1 ? 4 : 0
            body.addAttribute(
                .paragraphStyle,
                value: paragraph,
                range: NSRange(location: 0, length: body.length)
            )
            result.append(body)

            if index < lines.count - 1 {
                result.append(NSAttributedString(string: "\n", attributes: [.paragraphStyle: paragraph]))
            }
        }
        return result
    }

    private static func inline(
        _ input: String,
        base: [NSAttributedString.Key: Any]
    ) -> NSMutableAttributedString {
        let out = NSMutableAttributedString()
        let characters = Array(input)
        var index = 0

        func append(_ text: String, _ extra: [NSAttributedString.Key: Any] = [:]) {
            var attributes = base
            for (key, value) in extra { attributes[key] = value }
            out.append(NSAttributedString(string: text, attributes: attributes))
        }

        func boldFont() -> UIFont {
            let font = base[.font] as? UIFont ?? TextStyle.paragraph
            return UIFont.systemFont(ofSize: font.pointSize, weight: .bold)
        }

        while index < characters.count {
            if matches(characters, index, "**") {
                if let end = firstIndex(characters, "**", from: index + 2), end > index + 2 {
                    append(String(characters[(index + 2)..<end]), [.font: boldFont()])
                    index = end + 2
                    continue
                }
                append("**")
                index += 2
                continue
            }

            if characters[index] == "`" {
                if let end = firstIndex(characters, "`", from: index + 1), end > index + 1 {
                    append(String(characters[(index + 1)..<end]), [
                        .foregroundColor: Palette.accent,
                        .backgroundColor: Palette.inkElevated
                    ])
                    index = end + 1
                    continue
                }
                append("`")
                index += 1
                continue
            }

            if characters[index] == "[" {
                let labelEnd = firstIndex(characters, "]", from: index + 1)
                let urlEnd: Int? = {
                    guard let labelEnd, labelEnd + 1 < characters.count, characters[labelEnd + 1] == "(" else { return nil }
                    return firstIndex(characters, ")", from: labelEnd + 2)
                }()
                if let labelEnd, labelEnd > index + 1, let urlEnd, urlEnd > labelEnd + 2 {
                    append(String(characters[(index + 1)..<labelEnd]), [
                        .foregroundColor: Palette.accent,
                        .underlineStyle: NSUnderlineStyle.single.rawValue
                    ])
                    index = urlEnd + 1
                    continue
                }
                append("[")
                index += 1
                continue
            }

            if characters[index] == "*" || characters[index] == "_" {
                let marker = characters[index]
                if let end = firstIndex(characters, marker, from: index + 1), end > index + 1 {
                    let font = base[.font] as? UIFont ?? TextStyle.paragraph
                    append(String(characters[(index + 1)..<end]), [
                        .font: UIFont.italicSystemFont(ofSize: font.pointSize)
                    ])
                    index = end + 1
                    continue
                }
                append(String(marker))
                index += 1
                continue
            }

            append(String(characters[index]))
            index += 1
        }

        return out
    }

    private static func matches(_ characters: [Character], _ index: Int, _ needle: String) -> Bool {
        let target = Array(needle)
        guard index + target.count <= characters.count else { return false }
        return Array(characters[index..<(index + target.count)]) == target
    }

    private static func firstIndex(_ characters: [Character], _ target: Character, from: Int) -> Int? {
        var index = from
        while index < characters.count {
            if characters[index] == target { return index }
            index += 1
        }
        return nil
    }

    private static func firstIndex(_ characters: [Character], _ needle: String, from: Int) -> Int? {
        let target = Array(needle)
        guard !target.isEmpty, from >= 0 else { return nil }
        var index = from
        while index + target.count <= characters.count {
            if Array(characters[index..<(index + target.count)]) == target { return index }
            index += 1
        }
        return nil
    }
}
