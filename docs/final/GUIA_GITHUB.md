# Publicar POLYMER en un repositorio GitHub nuevo

> No se ha creado ni publicado ningún repositorio: estos son los comandos para que **tú** lo hagas cuando
> lo decidas. Nunca uses `git push --force` ni subas a un repositorio que ya tenga contenido.

## 1. Revisión de seguridad antes de publicar (resultados de esta auditoría)

| Comprobación | Comando | Resultado observado (12/11/2026) |
| --- | --- | --- |
| Ningún secreto rastreado | `git ls-files \| Select-String -Pattern "secrets/\|\.env$\|\.pem$\|\.key$"` | **Ninguno** (`secrets.example/` solo contiene plantillas `REEMPLAZA-…`) |
| Ningún archivo de secretos en todo el historial | `git log --all --name-only --format= \| Sort-Object -Unique \| Select-String "secrets/"` | **Ninguno** en los 50 commits |
| Contraseñas literales en el historial | búsqueda de `password=…`, `AKIA…`, `ghp_…`, claves privadas en `git log --all -p` | Solo valores **efímeros del CI** (`ci-only-…`), contraseñas de **pruebas** para clientes ficticios creados en entornos temporales y marcadores de plantilla. Ninguna credencial real |
| `.gitignore` | — | Excluye `secrets/`, `.env`, `.env.*` (salvo `.env.example`), `*.pem`, `*.key`, `target/`, `node_modules/`, capturas, logs |
| Volúmenes y copias | — | Los volúmenes de Docker y `/var/backups` están fuera del repositorio; `secrets/backup/` está dentro de `secrets/` (ignorado) |

Repite la revisión justo antes de publicar (por si añadiste archivos):

```powershell
git status --short
```

```powershell
git ls-files | Select-String -Pattern "secrets/|\.env$|\.pem$|\.key$|\.dump$"
```

(No debe mostrar nada; `secrets.example/` y `.env.example` sí pueden aparecer.)

Escáner opcional del historial con [gitleaks](https://github.com/gitleaks/gitleaks), en Docker:

```powershell
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest detect --source /repo --redact
```

(`--redact` evita mostrar valores. Revisa cada hallazgo: los valores `ci-only-…` son ficticios.)

## 2. Crear el repositorio nuevo

1. En https://github.com/new: nombre (por ejemplo `polymer`), **Private** o **Public**, y **sin** README,
   `.gitignore` ni licencia (debe estar vacío para no sobrescribir nada).
2. En la carpeta del proyecto:

```powershell
git remote add polymer https://github.com/TU_USUARIO/polymer.git
```

```powershell
git push -u polymer HEAD:main
```

Esto sube la rama actual como `main` con todo su historial (auditado arriba). El remoto anterior
(`origin`) no se modifica.

**Alternativa sin historial** (un único commit inicial):

```powershell
git checkout --orphan polymer-main
```

```powershell
git commit -m "POLYMER: versión inicial"
```

```powershell
git push -u polymer polymer-main:main
```

## 3. GitHub Actions

`.github/workflows/verify.yml` se ejecuta en cada push y pull request del repositorio nuevo, con 4 jobs:

| Job | Qué verifica | Duración aproximada |
| --- | --- | --- |
| `compose` | 13 contenedores, pruebas de humo, integraciones, degradación, persistencia, Playwright (UI, 45 páginas, intro, idiomas) y **escenarios de la exposición** | 20–30 min |
| `compose-basic` | Modo de 6 contenedores | 4–5 min |
| `windows` | Windows PowerShell 5.1: análisis de scripts, `polymer.cmd`, secretos, `check`, pruebas unitarias | 2–3 min |
| `migrate-from-v6` | Migración real desde HexaOrders v6 con datos y `change-password` | 8–10 min |

- No necesita secretos de GitHub: el CI usa contraseñas efímeras `ci-only-…` que solo existen durante la
  ejecución.
- En repositorios **privados** del plan gratuito, los minutos de Actions son limitados; los públicos no
  consumen minutos.
- El job `migrate-from-v6` reconstruye HexaOrders v6 desde el **historial de Git** (`git show 6cc87c0:…`).
  Funciona si publicas **con historial** (opción recomendada). Con la alternativa sin historial ese commit
  no existe y el job fallará: en ese caso desactívalo o ajústalo.

## 4. Contenido que se publica

Código completo (`services/`, `infra/`, `k8s/`, `tools/`), `docker-compose.yml` autosuficiente,
`polymer.cmd`, pruebas (Java, Python, Playwright, humo, escenarios), documentación (`README.md`, `docs/`,
`docs/final/`), `.env.example` y `secrets.example/`. **No** se publica: `secrets/`, `.env`, volúmenes,
copias de seguridad, `target/`, `node_modules/`, capturas de las pruebas.

## 5. Después de publicar

- Comprueba en **Actions** que los 4 jobs terminan en verde.
- Activa en **Settings → Code security** el *secret scanning* y la protección de *push* (gratuitos en
  repositorios públicos).
- Si alguna vez se subiera un secreto por error: **cámbialo de inmediato** (`.\polymer.cmd change-password`)
  y después límpialo del historial; borrar el archivo en un commit nuevo no basta.
