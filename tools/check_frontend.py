"""Comprobaciones estáticas del sitio multipágina (sin red ni Docker; se ejecuta en Windows y Linux).

1. Las páginas HTML coinciden con el registro de tools/generate_pages.py.
2. Cada página declara una ruta registrada por alguna vista (route('…') en static/js/views).
3. Hojas de estilo, scripts e imágenes enlazados por las páginas existen.
4. Todos los enlaces internos *.html escritos en HTML y JavaScript apuntan a archivos reales.
5. Ninguna página tiene estilos o scripts en línea (la Content-Security-Policy los bloquearía).
6. Las páginas protegidas tienen una regla en SecurityConfiguration con los mismos roles.
7. La tabla de transiciones de la documentación (JS) coincide con TransitionPolicy.java.
8. v6: la tienda (páginas públicas y del cliente, y los textos de sus vistas) no contiene explicaciones
   técnicas ni académicas (arquitectura, Docker, bases de datos, mensajería, observabilidad...) ni
   enlaza la documentación técnica interna, que solo ve el personal autorizado.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_pages import PAGES, STATIC, render  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'services/order-api/src/main/java/com/hexaorders'
errors: list[str] = []

# 1. Páginas al día
for page in PAGES:
    target = STATIC / page['path']
    if not target.exists() or target.read_text(encoding='utf-8').replace('\r\n', '\n') != render(page):
        errors.append(f'{page["path"]}: no coincide con el registro (ejecuta tools/generate_pages.py)')

# 2. Rutas registradas
views = '\n'.join(p.read_text(encoding='utf-8') for p in (STATIC / 'js/views').glob('*.js'))
registered = set(re.findall(r"route\('([^']+)'", views))
for page in PAGES:
    if page['route'] not in registered:
        errors.append(f'{page["path"]}: la ruta {page["route"]} no está registrada en ninguna vista')

# 3. Recursos enlazados
html_files = sorted(STATIC.rglob('*.html'))
for file in html_files:
    text = file.read_text(encoding='utf-8')
    for ref in re.findall(r'(?:href|src)="(/[^"#?]+\.(?:css|js|svg|png))"', text):
        if not (STATIC / ref.lstrip('/')).is_file():
            errors.append(f'{file.relative_to(STATIC)}: recurso inexistente {ref}')
    # 5. CSP: nada de style="" ni <script> en línea
    if re.search(r'\sstyle="', text):
        errors.append(f'{file.relative_to(STATIC)}: atributo style en línea (bloqueado por la CSP)')
    for script in re.findall(r'<script\b([^>]*)>', text):
        if 'src=' not in script:
            errors.append(f'{file.relative_to(STATIC)}: <script> en línea (bloqueado por la CSP)')

# 4. Enlaces internos a páginas
sources = [(f, f.read_text(encoding='utf-8')) for f in html_files + sorted((STATIC / 'js').rglob('*.js'))]
for file, text in sources:
    for ref in set(re.findall(r"""['"`](/[a-z0-9/_-]*\.html)""", text)):
        if not (STATIC / ref.lstrip('/')).is_file():
            errors.append(f'{file.relative_to(STATIC)}: enlace a página inexistente {ref}')

# 6. Seguridad de páginas protegidas
security = (JAVA / 'config/SecurityConfiguration.java').read_text(encoding='utf-8')
rules = re.findall(r'\.requestMatchers\(([^)]*)\)\.(hasRole|hasAnyRole|authenticated)\(([^)]*)\)', security)
def rule_for(path: str):
    for matchers, kind, roles in rules:
        for pattern in re.findall(r'"([^"]+)"', matchers):
            if pattern == path or (pattern.endswith('/**') and path.startswith(pattern[:-3] + '/')):
                return kind, set(re.findall(r'"([A-Z_]+)"', roles))
    return None
for page in PAGES:
    roles = set(page.get('roles') or [])
    if not roles:
        continue
    found = rule_for('/' + page['path'])
    if found is None:
        errors.append(f'{page["path"]}: protegida en el registro pero sin regla en SecurityConfiguration')
    elif roles == {'auth'}:
        if found[0] != 'authenticated':
            errors.append(f'{page["path"]}: debería exigir solo sesión (authenticated)')
    elif found[1] != roles:
        errors.append(f'{page["path"]}: roles del registro {sorted(roles)} ≠ SecurityConfiguration {sorted(found[1])}')

# 7. Transiciones documentadas = dominio
policy = (JAVA / 'domain/fulfillment/TransitionPolicy.java').read_text(encoding='utf-8')
java_rules = {(a, b, tuple(sorted(c.replace(' ', '').split(','))))
              for a, b, c in re.findall(r'allow\((\w+), (\w+), ([\w, ]+)\);', policy)}
js = (STATIC / 'js/views/architecture-pages.js').read_text(encoding='utf-8')
js_rules = {(a, b, tuple(sorted(re.findall(r"'(\w+)'", c))))
            for a, b, c in re.findall(r"\['(\w+)', '(\w+)', \[([^\]]*)\]\]", js)}
if not java_rules or java_rules != js_rules:
    errors.append(f'Transiciones distintas entre TransitionPolicy.java y architecture-pages.js: '
                  f'solo Java {sorted(java_rules - js_rules)}, solo JS {sorted(js_rules - java_rules)}')

# 8. Tienda sin contenido técnico ni académico
BANNED = ['hexagonal', 'puertos y adaptadores', 'adaptador de salida', 'adaptador de entrada', 'docker', 'contenedor', 'microservicio',
          'postgres', 'redis', 'rabbitmq', 'grafana', 'prometheus', 'idempoten', 'laboratorio', 'arquitectura', 'backend', 'spring',
          'flyway', 'motor de riesgo', 'jdbc', 'kubernetes', 'patrón de diseño', 'caso de uso', 'casos de uso', 'bcrypt', 'endpoint',
          'académic', 'microservicios', 'base de datos', 'servidor lo comprueba', 'api ']
store_pages = [page for page in PAGES if not page.get('section') or page.get('section') == 'cliente']
for page in store_pages:
    text = f"{page['title']} {page['description']}".lower()
    for word in BANNED:
        if word in text:
            errors.append(f'{page["path"]}: texto técnico en la tienda («{word}» en título o descripción)')
STORE_MODULES = ['js/views/home.js', 'js/views/shop.js', 'js/views/customer.js', 'js/store-ui.js', 'js/cart.js', 'js/layout.js']
string_literal = re.compile(r"'(?:[^'\\\n]|\\.)*'|\"(?:[^\"\\\n]|\\.)*\"|`(?:[^`\\]|\\.)*`")
for module in STORE_MODULES:
    source = (STATIC / module).read_text(encoding='utf-8')
    if module.endswith('layout.js'):
        # Las barras laterales de los portales del personal (SECTIONS) no forman parte de la tienda.
        start = source.index('export const SECTIONS = {')
        source = source[:start] + source[source.index('\n};', start):]
    for literal in string_literal.findall(source):
        value = literal[1:-1]
        if value.endswith('.js') or value.startswith(('.', '/api/')):
            continue
        lowered = value.lower() + ' '
        for word in BANNED:
            if word in lowered:
                errors.append(f'{module}: texto técnico en la tienda («{word}»): {value[:70]}')
        if re.search(r'/(arquitectura|laboratorio)', value):
            errors.append(f'{module}: la tienda enlaza la documentación técnica interna: {value[:70]}')

if errors:
    print('Frontend con problemas:\n  ' + '\n  '.join(errors), file=sys.stderr)
    sys.exit(1)
print(f'OK: frontend multipágina coherente ({len(PAGES)} páginas generadas + laboratorio, {len(registered)} rutas, '
      f'{len(java_rules)} transiciones, CSP sin estilos ni scripts en línea, {len(store_pages)} páginas de la tienda sin contenido técnico)')
