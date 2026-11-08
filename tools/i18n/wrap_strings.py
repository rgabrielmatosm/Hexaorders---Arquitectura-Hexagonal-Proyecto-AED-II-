#!/usr/bin/env python3
"""Codemod de una sola vez: envuelve en t(…) los textos visibles de los módulos de la interfaz.

Se conserva como registro de cómo se internacionalizó el frontend. Reglas:
  'Texto visible'        → t('Texto visible')
  `Hola, ${name}`        → t('Hola, {0}', name)
No toca: claves de objeto, rutas, clases CSS, selectores, códigos (MAYÚSCULAS), etiquetas HTML,
nombres de iconos, comentarios, expresiones regulares ni textos ya traducidos.
Uso: python3 tools/i18n/wrap_strings.py [--dry-run] archivo.js…
"""
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from js_strings import template_parts, tokens  # noqa: E402

LETTER = r'A-Za-zÁÉÍÓÚÜÑáéíóúüñ¿¡'
ACCENTED = re.compile(r'[ÁÉÍÓÚÜÑáéíóúüñ¿¡]')


def unescape(s):
    return s.replace("\\'", "'").replace('\\"', '"').replace('\\`', '`')


# Nombres propios, marcas y valores técnicos que nunca se traducen.
NEVER = {'Escape', 'Tab', 'Enter', 'AbortError', 'ApiError', 'IntersectionObserver', 'Facebook', 'Instagram', 'TikTok',
         'YouTube', 'LinkedIn', 'WhatsApp', 'Polymer', 'POLYMER', 'Grafana', 'Prometheus', 'PostgreSQL', 'Lenovo', 'Lima',
         'Redis', 'RabbitMQ', 'Mailpit', 'Loki', 'Tempo', 'Alloy', 'Slug', 'CMP-01-99'}


CLASS_WORDS = {'muted', 'small', 'mono', 'strong', 'center', 'right', 'left', 'hide-sm', 'eyebrow', 'lead'}


def visible(text):
    """¿Es texto para personas (no un identificador, ruta, clase o código)?"""
    t = text.strip()
    if t in NEVER or re.fullmatch(r'[\w.+-]+@[\w.-]+', t) or re.fullmatch(r'[A-Z0-9_]+(,[A-Z0-9_]+)+', t):
        return False
    if re.fullmatch(r'[\w-]+\([^)]*\)', t) or t.startswith(('var(', 'url(', 'cubic-bezier(')):
        return False
    # Valores CSS («drop-shadow(0 0 8px rgba(…))», «0px 0px -6% 0px»): sin palabras tras quitar funciones y unidades.
    residue = re.sub(r'[a-z-]+\(|\d+(\.\d+)?(px|%|ms|s|deg|em|rem|fr|vh|vw)?|[(),.#%-]', ' ', t)
    if not re.search(f'[{LETTER}]{{3,}}', residue):
        return False
    if t in {'noopener noreferrer', 'polite', 'assertive', 'off', 'on', 'true', 'false'}:
        return False
    if all(word in CLASS_WORDS for word in t.split()) or re.search(r'\b[a-z]+=[A-Za-z0-9]', t):
        return False
    if not re.search(f'[{LETTER}]{{2,}}', t):
        return False
    if t.startswith(('/', '#', '.', 'http', 'mailto:', 'data-', 'aria-', '[', 'polymer.', 'polymer:')):
        return False
    if re.fullmatch(r'[A-Z0-9_]+', t):                      # códigos: CUSTOMER, IN_TRANSIT
        return False
    if re.fullmatch(r'[a-z]{2}-[A-Z]{2}', t):              # locales es-PE
        return False
    if re.fullmatch(r'[a-z0-9_.:/=?&%-]+', t):             # identificadores, clases, rutas en minúsculas
        return False
    if re.fullmatch(r'[a-z0-9_-]+( [a-z0-9_-]+)+', t) and ('--' in t or '__' in t or '-' in t):
        return False                                        # «btn btn--primary», «card form»
    if re.fullmatch(r'[a-z0-9_-]+( [a-z0-9_-]+)+', t) and not ACCENTED.search(t) and t.split()[0] in {
            'btn', 'card', 'field', 'view', 'grid', 'row', 'badge', 'chip', 'icon', 'nav', 'store', 'notice', 'form', 'table', 'pw'}:
        return False
    if re.search(r'\.(html|js|css|svg|json|txt)\b', t) or '=>' in t or t.startswith('<'):
        return False
    if re.fullmatch(r'[\w-]+(\[[^\]]*\])?([ >+~][\w.#:\[\]=\"-]+)*', t) and not ' ' in t and t[0].islower():
        return False
    return True


def object_key(src, start, end):
    """El literal es una clave de objeto: precedido por { o , y seguido de :."""
    after = src[end:].lstrip()
    before = src[:start].rstrip()
    return after.startswith(':') and before.endswith(('{', ','))


def in_comparison(src, start, end):
    """Comparaciones y case: cambiar el texto rompería la lógica."""
    return bool(re.search(r'(===|!==|==|!=)\s*$', src[:start]) or re.match(r'\s*(===|!==|==|!=)', src[end:])
                or re.search(r'\bcase\s+$', src[:start]))


def already_wrapped(src, start):
    return bool(re.search(r'\b(t|td)\(\s*$', src[:start]))


def first_arg_of(src, start, names):
    return bool(re.search(r'\b(' + '|'.join(names) + r')\(\s*$', src[:start]))


SKIP_CALLS = ['querySelector', 'querySelectorAll', 'closest', 'matches', 'getAttribute', 'setAttribute', 'removeAttribute',
              'hasAttribute', 'addEventListener', 'removeEventListener', 'CustomEvent', 'createElement', 'createElementNS',
              'getItem', 'setItem', 'removeItem', 'icon', 'el', 'fetch', 'import', 'add', 'remove', 'toggle', 'contains',
              'getPropertyValue', 'setProperty', 'Intl.NumberFormat', 'NumberFormat', 'DateTimeFormat', 'RelativeTimeFormat',
              'matchMedia', 'require', 'get', 'has', 'set', 'delete', 'append', 'play', 'animate']


def rewrite(src):
    out, last, count = [], 0, 0
    for start, end, quote in tokens(src):
        literal = src[start:end]
        line_start = src.rfind('\n', 0, start) + 1
        line = src[line_start:src.find('\n', start)]
        if line.lstrip().startswith(('import ', 'export {')) or ' from ' in line and line.lstrip().startswith('import'):
            continue
        if object_key(src, start, end) or in_comparison(src, start, end) or already_wrapped(src, start) or first_arg_of(src, start, SKIP_CALLS):
            continue
        if quote == '`':
            statics, exprs = template_parts(literal)
            static_text = ''.join(statics)
            if not visible(static_text) or '\n' in static_text or not re.search(r'[\s' + ACCENTED.pattern[1:-1] + ']', static_text):
                continue
            message = ''.join(s + (f'{{{k}}}' if k < len(exprs) else '') for k, s in enumerate(statics))
            message = unescape(message).replace('\\', '\\\\').replace("'", "\\'")
            args = ''.join(', ' + e.strip() for e in exprs)
            replacement = f"t('{message}'{args})"
        else:
            value = literal[1:-1]
            if not visible(unescape(value)):
                continue
            if quote == '"':
                literal = "'" + unescape(value).replace('\\', '\\\\').replace("'", "\\'") + "'"
            replacement = f't({literal})'
        out.append(src[last:start])
        out.append(replacement)
        last = end
        count += 1
    out.append(src[last:])
    return ''.join(out), count


def ensure_import(src, path):
    if re.search(r"import \{[^}]*\bt\b[^}]*\} from '[./]*i18n\.js'", src):
        return src
    rel = "'../i18n.js'" if '/views/' in str(path) else "'./i18n.js'"
    imports = list(re.finditer(r'^import [^;]+;\n', src, re.M))
    pos = imports[-1].end() if imports else 0
    return src[:pos] + f'import {{ t }} from {rel};\n' + src[pos:]


def main(argv):
    dry = '--dry-run' in argv
    for name in [a for a in argv if not a.startswith('--')]:
        path = pathlib.Path(name)
        src = path.read_text(encoding='utf-8')
        new, count = rewrite(src)
        if count:
            new = ensure_import(new, path)
        print(f'{count:4d}  {name}')
        if not dry and count:
            path.write_text(new, encoding='utf-8')


if __name__ == '__main__':
    main(sys.argv[1:])
