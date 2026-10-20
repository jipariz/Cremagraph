"""Outline a word in a TrueType font as one SVG path, with the standard library only.

Reads cmap (format 4), hmtx, loca and simple glyf glyphs, and turns each quadratic contour into SVG path commands.
Kerning is not applied: the logo's spacing is set by hand in build.py.
"""
import struct


class Font:
    def __init__(self, path):
        self.d = open(path, 'rb').read()
        n = struct.unpack('>H', self.d[4:6])[0]
        self.t = {}
        for i in range(n):
            tag, _, off, ln = struct.unpack('>4sIII', self.d[12 + 16 * i:28 + 16 * i])
            self.t[tag.decode()] = (off, ln)
        h = self.t['head'][0]
        self.upm = struct.unpack('>H', self.d[h + 18:h + 20])[0]
        self.loca_long = struct.unpack('>h', self.d[h + 50:h + 52])[0] == 1
        self.nglyphs = struct.unpack('>H', self.d[self.t['maxp'][0] + 4:self.t['maxp'][0] + 6])[0]
        hh = self.t['hhea'][0]
        self.nhm = struct.unpack('>H', self.d[hh + 34:hh + 36])[0]
        self.cmap = self._cmap()

    def _cmap(self):
        c = self.t['cmap'][0]
        n = struct.unpack('>H', self.d[c + 2:c + 4])[0]
        for i in range(n):
            pid, eid, off = struct.unpack('>HHI', self.d[c + 4 + 8 * i:c + 12 + 8 * i])
            s = c + off
            if struct.unpack('>H', self.d[s:s + 2])[0] == 4 and pid in (0, 3):
                seg2 = struct.unpack('>H', self.d[s + 6:s + 8])[0]
                seg = seg2 // 2
                ends = struct.unpack(f'>{seg}H', self.d[s + 14:s + 14 + seg2])
                starts = struct.unpack(f'>{seg}H', self.d[s + 16 + seg2:s + 16 + 2 * seg2])
                deltas = struct.unpack(f'>{seg}h', self.d[s + 16 + 2 * seg2:s + 16 + 3 * seg2])
                ro_at = s + 16 + 3 * seg2
                ros = struct.unpack(f'>{seg}H', self.d[ro_at:ro_at + seg2])
                m = {}
                for k in range(seg):
                    for code in range(starts[k], ends[k] + 1):
                        if code == 0xFFFF:
                            continue
                        if ros[k] == 0:
                            g = (code + deltas[k]) & 0xFFFF
                        else:
                            a = ro_at + 2 * k + ros[k] + 2 * (code - starts[k])
                            g = struct.unpack('>H', self.d[a:a + 2])[0]
                            g = (g + deltas[k]) & 0xFFFF if g else 0
                        m[code] = g
                return m
        raise ValueError('no format-4 cmap')

    def advance(self, g):
        hm = self.t['hmtx'][0]
        i = min(g, self.nhm - 1)
        return struct.unpack('>H', self.d[hm + 4 * i:hm + 4 * i + 2])[0]

    def _loca(self, g):
        l = self.t['loca'][0]
        if self.loca_long:
            a, b = struct.unpack('>II', self.d[l + 4 * g:l + 4 * g + 8])
        else:
            a, b = struct.unpack('>HH', self.d[l + 2 * g:l + 2 * g + 4]); a, b = a * 2, b * 2
        return a, b

    def contours(self, g):
        a, b = self._loca(g)
        if a == b:
            return []
        p = self.t['glyf'][0] + a
        nc = struct.unpack('>h', self.d[p:p + 2])[0]
        if nc < 0:
            raise ValueError(f'composite glyph {g} not supported')
        ends = struct.unpack(f'>{nc}H', self.d[p + 10:p + 10 + 2 * nc])
        q = p + 10 + 2 * nc
        il = struct.unpack('>H', self.d[q:q + 2])[0]
        q += 2 + il
        npts = ends[-1] + 1
        flags = []
        while len(flags) < npts:
            f = self.d[q]; q += 1
            flags.append(f)
            if f & 8:
                r = self.d[q]; q += 1
                flags += [f] * r
        def coords(short, same):
            nonlocal q
            out, v = [], 0
            for f in flags:
                if f & short:
                    dv = self.d[q]; q += 1
                    v += dv if f & same else -dv
                elif not f & same:
                    v += struct.unpack('>h', self.d[q:q + 2])[0]; q += 2
                out.append(v)
            return out
        xs = coords(2, 16); ys = coords(4, 32)
        res, s = [], 0
        for e in ends:
            res.append([(xs[i], ys[i], bool(flags[i] & 1)) for i in range(s, e + 1)]); s = e + 1
        return res


def contour_path(pts, ox, oy, sc):
    # Expand implied on-curve points, then emit M/Q/L, flipping y.
    full = []
    for i, (x, y, on) in enumerate(pts):
        nx, ny, non = pts[(i + 1) % len(pts)]
        full.append((x, y, on))
        if not on and not non:
            full.append(((x + nx) / 2, (y + ny) / 2, True))
    k = next(i for i, p in enumerate(full) if p[2])
    full = full[k:] + full[:k]
    T = lambda x, y: (round(ox + x * sc, 2), round(oy - y * sc, 2))
    x0, y0 = T(full[0][0], full[0][1])
    d = [f'M{x0} {y0}']
    i = 1
    n = len(full)
    while i <= n:
        x, y, on = full[i % n]
        if on:
            X, Y = T(x, y); d.append(f'L{X} {Y}'); i += 1
        else:
            cx, cy = T(x, y); ex, ey, _ = full[(i + 1) % n]; X, Y = T(ex, ey)
            d.append(f'Q{cx} {cy} {X} {Y}'); i += 2
    d.append('Z')
    return ''.join(d)


def word_path(font, text, x, baseline, size, tracking=0.0, adjust=None):
    """The word as one path; [tracking] in em added between letters, [adjust] per-pair em tweaks."""
    sc = size / font.upm
    out, pen = [], x
    for i, ch in enumerate(text):
        g = font.cmap[ord(ch)]
        for c in font.contours(g):
            out.append(contour_path(c, pen, baseline, sc))
        pen += font.advance(g) * sc + tracking * size
        if adjust and i + 1 < len(text):
            pen += adjust.get(text[i:i + 2], 0) * size
    return ''.join(out), pen - tracking * size
