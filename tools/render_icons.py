#!/usr/bin/env python3
"""Rasterize the parsed miuix icon paths so the geometry can be eyeballed.

Deliberately standalone: the point is to validate the *parsed* data (y-flip,
fill rule, coordinates) independently of anything that will ship, since the
Swift renderer cannot be compiled in this environment.
"""
import io, json, math, sys

ICONS = json.load(io.open('/workspace/build/icons.json'))
SIZE = 96
BG = (0x10, 0x10, 0x10)
FG = (0xF2, 0xF2, 0xF2)


def flatten(nodes):
    """Return subpaths as lists of (x, y) points, plus whether each is closed."""
    subs, cur = [], []
    x = y = 0.0
    for op, a in nodes:
        if op == 'MoveTo':
            if cur:
                subs.append(cur)
            x, y = a
            cur = [(x, y)]
        elif op == 'LineTo':
            x, y = a
            cur.append((x, y))
        elif op == 'HorizontalTo':
            x = a[0]
            cur.append((x, y))
        elif op == 'VerticalTo':
            y = a[0]
            cur.append((x, y))
        elif op == 'QuadTo':
            cx, cy, ex, ey = a
            for i in range(1, 17):
                t = i / 16
                u = 1 - t
                cur.append((u * u * x + 2 * u * t * cx + t * t * ex,
                            u * u * y + 2 * u * t * cy + t * t * ey))
            x, y = ex, ey
        elif op == 'CurveTo':
            c1x, c1y, c2x, c2y, ex, ey = a
            for i in range(1, 17):
                t = i / 16
                u = 1 - t
                cur.append((u**3 * x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t**3 * ex,
                            u**3 * y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t**3 * ey))
            x, y = ex, ey
        elif op == 'Close':
            if cur:
                subs.append(cur)
                cur = []
    if cur:
        subs.append(cur)
    return subs


def coverage(subs, even_odd, px, py):
    """Sample one point against the flattened subpaths."""
    winding = 0
    crossings = 0
    for poly in subs:
        n = len(poly)
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if (y1 > py) == (y2 > py):
                continue
            t = (py - y1) / (y2 - y1)
            xint = x1 + t * (x2 - x1)
            if xint > px:
                crossings += 1
                winding += 1 if y2 > y1 else -1
    return (crossings % 2 == 1) if even_odd else (winding != 0)


def render(icon):
    data = ICONS[icon]
    viewport = data['viewport']
    scale = SIZE / viewport
    buf = [bytearray(BG * SIZE) for _ in range(SIZE)]
    for even_odd, nodes in data['paths']:
        # Compose authors icons y-up; the source applies scaleY = -1.
        subs = [[(x * scale, (viewport - y) * scale) for x, y in poly] for poly in flatten(nodes)]
        for row in range(SIZE):
            for col in range(SIZE):
                if coverage(subs, even_odd, col + 0.5, row + 0.5):
                    o = col * 3
                    buf[row][o:o + 3] = bytes(FG)
    out = bytearray()
    for row in buf:
        out += row
    return bytes(out)


def main():
    names = sys.argv[1:] or sorted(ICONS)
    for name in names:
        pixels = render(name)
        io.open('/workspace/build/ppm/%s.ppm' % name, 'wb').write(b'P6\n%d %d\n255\n' % (SIZE, SIZE) + pixels)
    print('rendered', len(names), 'icons')


if __name__ == '__main__':
    main()
