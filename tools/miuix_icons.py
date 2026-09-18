#!/usr/bin/env python3
"""Convert miuix-icon Kotlin ImageVector sources into a compact Swift icon table.

Emits:
  build/icons/<name>.svg   -- for visual verification
  build/MiuixIcons.swift   -- the table the app renders from
"""
import io, os, re, sys, json

SRC = '/tmp/miuix-icons/out/commonMain/top/yukonga/miuix/kmp/icon/extended'
OUT = '/workspace/build/icons'
SWIFT = '/workspace/app/iosApp/iosApp/Design/MiuixIcons.swift'

# Android usage -> miuix icon name. FavoritesFill is the filled heart for "liked".
ICONS = {
    'add': 'Add', 'back': 'Back', 'favorites': 'Favorites', 'favoritesFilled': 'FavoritesFill',
    'home': 'Home', 'link': 'Link', 'more': 'More', 'ok': 'Ok', 'photos': 'Photos',
    'refresh': 'Refresh', 'reply': 'Reply', 'search': 'Search', 'settings': 'Settings',
    'share': 'Share', 'delete': 'Delete', 'pin': 'Pin', 'unpin': 'Unpin', 'rename': 'Rename',
}

NUM = r'-?\d+(?:\.\d+)?'
OP_ARGS = {'MoveTo': 2, 'LineTo': 2, 'HorizontalTo': 1, 'VerticalTo': 1, 'QuadTo': 4, 'CurveTo': 6, 'Close': 0}
OPCODE = {'MoveTo': 0, 'LineTo': 1, 'QuadTo': 2, 'CurveTo': 3, 'Close': 4, 'HorizontalTo': 5, 'VerticalTo': 6}


def read_regular(name):
    path = os.path.join(SRC, name + '.kt')
    text = io.open(path, encoding='utf-8').read()
    m = re.search(r'^val MiuixIcons\.Regular\.' + name + r': ImageVector', text, re.M)
    if not m:
        raise SystemExit('no Regular variant for ' + name)
    rest = text[m.end():]
    nxt = re.search(r'^val MiuixIcons\.', rest, re.M)
    block = rest[:nxt.start()] if nxt else rest

    vw = re.search(r'viewportWidth = (%s)f' % NUM, block)
    if not vw:
        raise SystemExit('no viewport for ' + name)
    viewport = float(vw.group(1))

    paths = []
    # Split on addPath( rather than regex-matching to its closing paren: the
    # argument list itself contains ")," from every PathNode.*(...) call.
    for part in block.split('addPath(')[1:]:
        nodes = []
        for nm in re.finditer(r'PathNode\.(\w+)(?:\(([^)]*)\))?', part):
            op = nm.group(1)
            if op not in OP_ARGS:
                raise SystemExit('unhandled PathNode.' + op + ' in ' + name)
            args = [float(x) for x in re.findall(NUM, nm.group(2) or '')]
            if len(args) != OP_ARGS[op]:
                raise SystemExit('arity mismatch on PathNode.%s in %s: %s' % (op, name, args))
            nodes.append((op, args))
        ft = re.search(r'pathFillType = PathFillType\.(\w+)', part)
        if ft and ft.group(1) == 'EvenOdd':
            # Swift's renderer fills every path with the default rule; add
            # support here at the same time rather than emitting wrong shapes.
            raise SystemExit('EvenOdd fill found in ' + name + '; renderer needs updating')
        paths.append((False, nodes))
    if not paths:
        raise SystemExit('no paths for ' + name)
    return viewport, paths


def svg(viewport, paths):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %g %g" width="%g" height="%g">'
           % (viewport, viewport, viewport, viewport)]
    # Compose icons are authored y-up, so the source wraps them in scaleY = -1.
    out.append('<g transform="translate(0,%g) scale(1,-1)">' % viewport)
    for even_odd, nodes in paths:
        d = []
        for op, a in nodes:
            if op == 'MoveTo':
                d.append('M%g %g' % (a[0], a[1]))
            elif op == 'LineTo':
                d.append('L%g %g' % (a[0], a[1]))
            elif op == 'HorizontalTo':
                d.append('H%g' % a[0])
            elif op == 'VerticalTo':
                d.append('V%g' % a[0])
            elif op == 'QuadTo':
                d.append('Q%g %g %g %g' % (a[0], a[1], a[2], a[3]))
            elif op == 'CurveTo':
                d.append('C%g %g %g %g %g %g' % tuple(a))
            elif op == 'Close':
                d.append('Z')
        out.append('<path d="%s" fill="#ffffff" fill-rule="%s"/>' % (' '.join(d), 'evenodd' if even_odd else 'nonzero'))
    out.append('</g></svg>')
    return '\n'.join(out)


def flat_ops(nodes):
    out = []
    for op, a in nodes:
        out.append(OPCODE[op])
        out.extend(a)
    return out


def swift(parsed):
    """Emit the icon table. Each icon's op list is its own declaration so no single
    Swift expression is large enough to slow the type checker."""
    head = '''import UIKit

// Generated from miuix-icons (Apache-2.0) by tools/miuix_icons.py — regenerate
// rather than editing. Rendering the vectors here instead of shipping an asset
// catalog keeps the geometry reviewable as text.

/// The miuix outline icons the Android UI draws.
enum MiuixIcon {
    case add, back, favorites, favoritesFilled, home, link, more, ok, photos
    case refresh, reply, search, settings, share, delete, pin, unpin, rename
}

private struct IconPath {
    let ops: [CGFloat]
}

private struct IconData {
    let viewport: CGFloat
    let paths: [IconPath]
}

enum MiuixIcons {
    /// Opcodes: 0 move(x,y), 1 line(x,y), 2 quad(cx,cy,x,y),
    /// 3 curve(c1x,c1y,c2x,c2y,x,y), 4 close, 5 horizontal(x), 6 vertical(y).
    static func image(_ icon: MiuixIcon, size: CGFloat, color: UIColor) -> UIImage {
        let data = table[icon]!
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        return renderer.image { context in
            // Icons are authored y-up, so flip into UIKit's coordinate space.
            let cg = context.cgContext
            let scale = size / data.viewport
            cg.translateBy(x: 0, y: size)
            cg.scaleBy(x: scale, y: -scale)
            color.setFill()
            for path in data.paths {
                let bezier = UIBezierPath()
                append(path.ops, to: bezier)
                bezier.fill()
            }
        }.withRenderingMode(.alwaysOriginal)
    }

    private static func append(_ ops: [CGFloat], to path: UIBezierPath) {
        var index = 0
        while index < ops.count {
            let opcode = Int(ops[index])
            index += 1
            switch opcode {
            case 0:
                path.move(to: CGPoint(x: ops[index], y: ops[index + 1])); index += 2
            case 1:
                path.addLine(to: CGPoint(x: ops[index], y: ops[index + 1])); index += 2
            case 2:
                path.addQuadCurve(
                    to: CGPoint(x: ops[index + 2], y: ops[index + 3]),
                    controlPoint: CGPoint(x: ops[index], y: ops[index + 1])
                ); index += 4
            case 3:
                path.addCurve(
                    to: CGPoint(x: ops[index + 4], y: ops[index + 5]),
                    controlPoint1: CGPoint(x: ops[index], y: ops[index + 1]),
                    controlPoint2: CGPoint(x: ops[index + 2], y: ops[index + 3])
                ); index += 6
            case 4:
                path.close()
            case 5:
                path.addLine(to: CGPoint(x: ops[index], y: path.currentPoint.y)); index += 1
            default:
                path.addLine(to: CGPoint(x: path.currentPoint.x, y: ops[index])); index += 1
            }
        }
    }
}

'''

    def num(v):
        return ('%g' % v)

    ops_decls = []
    for key in sorted(parsed):
        viewport, paths = parsed[key]
        parts = []
        for even_odd, nodes in paths:
            flat = flat_ops(nodes)
            parts.append('        IconPath(ops: %sOps)' % key)
        ops_decls.append((key, viewport, parts, paths))

    out = [head]
    for key, viewport, parts, paths in ops_decls:
        nums = []
        for even_odd, nodes in paths:
            nums.extend(flat_ops(nodes))
        body = ', '.join(num(n) for n in nums)
        out.append('private let %sOps: [CGFloat] = [%s]\n' % (key, body))

    out.append('private let table: [MiuixIcon: IconData] = [')
    for key, viewport, parts, paths in ops_decls:
        out.append('    .%s: IconData(viewport: %s, paths: [' % (key, num(viewport)))
        for p in parts:
            out.append('    ' + p + ',')
        out.append('    ]),')
    out.append(']\n')
    return '\n'.join(out)


def main():
    os.makedirs(OUT, exist_ok=True)
    parsed = {}
    for key, name in ICONS.items():
        viewport, paths = read_regular(name)
        parsed[key] = (viewport, paths)
        io.open(os.path.join(OUT, key + '.svg'), 'w', encoding='utf-8').write(svg(viewport, paths))
        print('%-16s viewport=%-9g paths=%d nodes=%d' % (key, viewport, len(paths), sum(len(n) for _, n in paths)))

    json.dump({k: {'viewport': v, 'paths': [[eo, n] for eo, n in p]} for k, (v, p) in parsed.items()},
              io.open('/workspace/build/icons.json', 'w'), indent=1)
    io.open(SWIFT, 'w', encoding='utf-8').write(swift(parsed))
    print('\nwrote', OUT, ', icons.json and', SWIFT)


if __name__ == '__main__':
    main()
