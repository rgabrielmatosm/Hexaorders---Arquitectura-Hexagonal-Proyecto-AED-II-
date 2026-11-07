#!/usr/bin/env python3
"""Genera los SVG de la marca POLYMER desde una sola definición geométrica.

Isotipo «Enlace» (concepto A refinado, ver docs/brand/BRAND_POLYMER.md): la P se
traza como una pista de circuito con esquinas biseladas. Tres nodos (monómeros)
se enlazan en una cadena —el polímero—, y una señal corta sale del lazo para
indicar movimiento y conexión. No usa hexágonos ni formas de otras marcas.

Uso:
    python3 tools/generate_brand.py          # escribe los SVG
    python3 tools/generate_brand.py --check  # falla si algún SVG no coincide
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
IMG = ROOT / 'services/order-api/src/main/resources/static/img'

INK, GRAPHITE, TEAL, PERIWINKLE, SNOW = '#101827', '#171D2B', '#3DE2D0', '#A6B8FF', '#F8FAFC'

# ── Isotipo (rejilla de 64) ─────────────────────────────────────────────────
STEM = 'M18 50 V14'
LOOP = 'M18 14 H37 L47 24 V29 L37 39 H27'
INNER = 'M27 22 H34 L38 26 V27 L34 31 H27'
SIGNAL = 'M51 26.5 H56'
NODES = [(18, 51, 3.8), (27, 39, 3.3), (27, 22, 3.3)]
CORE = (27, 31, 2.1)

# ── Logotipo «POLYMER» con trazos geométricos (rejilla de 20 de alto) ───────
LETTERS = [
    (13, 'M0 20 V0 H9 L13 4 V7 L9 11 H0'),                    # P
    (14, 'M4 0 H10 L14 4 V16 L10 20 H4 L0 16 V4 Z'),          # O
    (12, 'M0 0 V20 H12'),                                     # L
    (14, 'M0 0 L7 9 L14 0 M7 9 V20'),                         # Y
    (16, 'M0 20 V0 L8 10 L16 0 V20'),                         # M
    (12, 'M12 0 H0 V20 H12 M0 10 H9'),                        # E
    (13, 'M0 20 V0 H9 L13 4 V7 L9 11 H0 M7 11 L13 20'),       # R
]
GAP = 7


def gradient(gid):
    return (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="14" y1="10" x2="54" y2="54">'
            f'<stop offset="0" stop-color="{TEAL}"/><stop offset="1" stop-color="{PERIWINKLE}"/></linearGradient>')


def mark(stroke, node_fill, core, gid=None, extra=''):
    paths = ''.join(f'<path d="{d}"{extra}/>' for d in (STEM, LOOP, INNER))
    nodes = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}"/>' for x, y, r in NODES)
    return (f'<g fill="none" stroke="{stroke}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round">'
            f'{paths}<path d="{SIGNAL}" opacity=".7"/></g>'
            f'<g fill="{node_fill}" stroke="{stroke}" stroke-width="2.6">{nodes}</g>'
            f'<circle cx="{CORE[0]}" cy="{CORE[1]}" r="{CORE[2]}" fill="{core}"/>')


def wordmark(color, x, y, scale):
    parts, cursor = [], 0
    for width, d in LETTERS:
        parts.append(f'<path transform="translate({cursor} 0)" d="{d}"/>')
        cursor += width + GAP
    return (f'<g transform="translate({x} {y}) scale({scale})" fill="none" stroke="{color}" stroke-width="2.6" '
            f'stroke-linecap="square" stroke-linejoin="miter">{"".join(parts)}</g>'), (cursor - GAP) * scale


def svg(view_box, body, title, defs=''):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}" role="img" aria-label="{title}">'
            f'<title>{title}</title>{"<defs>" + defs + "</defs>" if defs else ""}{body}</svg>\n')


def lockup(word_color, tile, mono=None):
    stroke = mono or 'url(#pm-g)'
    defs = '' if mono else gradient('pm-g')
    tile_rect = f'<rect width="64" height="64" rx="15" fill="{tile}"/>' if tile else ''
    node_fill = tile or ('none' if mono else INK)
    word, width = wordmark(mono or word_color, 82, 21, 1.1)
    total = int(82 + width + 4)
    return svg(f'0 0 {total} 64', tile_rect + mark(stroke, node_fill if tile else (INK if not mono else 'none'), mono or TEAL) + word,
               'POLYMER', defs)


def animated():
    """Versión animada con SMIL (sin <style>, compatible con la CSP del sitio)."""
    defs = gradient('pa-g')
    lines = ''
    for i, d in enumerate((STEM, LOOP, INNER)):
        lines += (f'<path d="{d}" pathLength="1" stroke-dasharray="1" stroke-dashoffset="1">'
                  f'<animate attributeName="stroke-dashoffset" from="1" to="0" begin="{0.15 * i:.2f}s" dur=".9s" fill="freeze" '
                  f'calcMode="spline" keySplines=".2 .7 .2 1"/></path>')
    nodes = ''.join(
        f'<circle cx="{x}" cy="{y}" r="{r}" opacity="0"><animate attributeName="opacity" from="0" to="1" '
        f'begin="{0.7 + 0.12 * i:.2f}s" dur=".35s" fill="freeze"/></circle>' for i, (x, y, r) in enumerate(NODES))
    signal = (f'<path d="{SIGNAL}" stroke-dasharray="5 20" stroke-dashoffset="5"><animate attributeName="stroke-dashoffset" '
              f'values="5;-20" begin="1.2s" dur="1.6s" repeatCount="indefinite"/></path>')
    core = (f'<circle cx="{CORE[0]}" cy="{CORE[1]}" r="{CORE[2]}" fill="{TEAL}"><animate attributeName="r" '
            f'values="{CORE[2]};3.2;{CORE[2]}" begin="1.2s" dur="2.4s" repeatCount="indefinite"/></circle>')
    body = (f'<rect width="64" height="64" rx="15" fill="{INK}"/>'
            f'<g fill="none" stroke="url(#pa-g)" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round">{lines}{signal}</g>'
            f'<g fill="{INK}" stroke="url(#pa-g)" stroke-width="2.6">{nodes}</g>{core}')
    return svg('0 0 64 64', body, 'POLYMER', defs)


def outputs():
    tile_mark = svg('0 0 64 64', f'<rect width="64" height="64" rx="15" fill="{INK}"/>' + mark('url(#pm-g)', INK, TEAL),
                    'POLYMER', gradient('pm-g'))
    bare_mark = svg('0 0 64 64', mark('url(#pm-g)', INK, TEAL), 'POLYMER', gradient('pm-g'))
    return {
        IMG / 'logo.svg': tile_mark,                         # favicon y referencias existentes
        IMG / 'polymer-mark.svg': bare_mark,                 # isotipo sin fondo (sobre oscuro)
        IMG / 'polymer-mark-animated.svg': animated(),
        IMG / 'polymer-logo.svg': lockup(SNOW, None),        # horizontal para fondo oscuro
        IMG / 'polymer-logo-light.svg': lockup(INK, None),   # horizontal para fondo claro
        IMG / 'polymer-logo-mono.svg': lockup(None, None, mono=SNOW),
        IMG / 'polymer-logo-mono-dark.svg': lockup(None, None, mono=INK),
    }


def main():
    check = '--check' in sys.argv
    stale = []
    for path, content in outputs().items():
        if check:
            if not path.exists() or path.read_text(encoding='utf-8') != content:
                stale.append(path.relative_to(ROOT))
        else:
            path.write_text(content, encoding='utf-8')
    if stale:
        print('SVG de marca desactualizados (ejecuta tools/generate_brand.py):', *stale, sep='\n  ')
        return 1
    print('Marca POLYMER: ' + ('al día' if check else f'{len(outputs())} SVG escritos'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
