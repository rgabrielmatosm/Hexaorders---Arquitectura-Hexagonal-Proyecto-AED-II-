"""Tokenizador mínimo de JavaScript para el codemod de i18n (wrap_strings.py) y para check_i18n.py.

Devuelve los literales de cadena ('…', "…", `…`) con su posición, ignorando comentarios y
expresiones regulares. Las plantillas se analizan por partes estáticas y expresiones ${…}.
"""
import re

REGEX_PRECEDERS = set('(,=:[!&|?{};+-*%<>~^')


def _skip_string(src, i, quote):
    j = i + 1
    while j < len(src):
        c = src[j]
        if c == '\\':
            j += 2
            continue
        if quote == '`' and c == '$' and j + 1 < len(src) and src[j + 1] == '{':
            j = _skip_expression(src, j + 2)
            continue
        if c == quote:
            return j + 1
        if quote != '`' and c == '\n':
            return j  # cadena sin cerrar (no debería ocurrir)
        j += 1
    return j


def _skip_expression(src, j):
    """Desde dentro de ${, devuelve el índice tras la } que cierra la expresión."""
    depth = 1
    while j < len(src) and depth:
        c = src[j]
        if c in '\'"`':
            j = _skip_string(src, j, c)
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
        j += 1
    return j


def tokens(src):
    """Itera (inicio, fin, comilla) de cada literal de cadena de nivel superior."""
    i, n = 0, len(src)
    prev = ''
    while i < n:
        c = src[i]
        if c == '/' and src.startswith('//', i):
            i = src.find('\n', i)
            i = n if i < 0 else i
            continue
        if c == '/' and src.startswith('/*', i):
            i = src.find('*/', i + 2)
            i = n if i < 0 else i + 2
            continue
        if c == '/' and (prev in REGEX_PRECEDERS or prev == '' or re.match(r'.*\b(return|typeof|case)\s*$', src[max(0, i - 12):i])):
            j = i + 1
            in_class = False
            while j < n and src[j] != '\n':
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == '[':
                    in_class = True
                elif src[j] == ']':
                    in_class = False
                elif src[j] == '/' and not in_class:
                    break
                j += 1
            i = j + 1
            while i < n and src[i].isalpha():
                i += 1
            prev = 'x'
            continue
        if c in '\'"`':
            j = _skip_string(src, i, c)
            yield i, j, c
            i = j
            prev = 'x'
            continue
        if not c.isspace():
            prev = c
        i += 1


def template_parts(literal):
    """Para `a ${x} b ${y}` devuelve (['a ', ' b ', ''], ['x', 'y'])."""
    body = literal[1:-1]
    statics, exprs, buf, i = [], [], '', 0
    while i < len(body):
        c = body[i]
        if c == '\\':
            buf += body[i:i + 2]
            i += 2
            continue
        if c == '$' and i + 1 < len(body) and body[i + 1] == '{':
            end = _skip_expression(body, i + 2)
            statics.append(buf)
            exprs.append(body[i + 2:end - 1])
            buf = ''
            i = end
            continue
        buf += c
        i += 1
    statics.append(buf)
    return statics, exprs
