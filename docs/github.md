# Publicar HexaOrders en tu GitHub

Ejecuta estos pasos desde la raíz del proyecto con Git y [GitHub CLI](https://cli.github.com/) instalados. Se publican el YAML autosuficiente y el material de apoyo. Las credenciales se generan por máquina y se excluyen mediante `.gitignore`.

## 1. Preparar el repositorio local

En PowerShell, reemplaza nombre y correo por los de tu cuenta:

```powershell
Set-Location 'C:\Users\frank\Downloads\PROYECTO DOCKER'
git init -b main
git config user.name 'TU NOMBRE'
git config user.email 'TU_CORREO_O_NOREPLY_DE_GITHUB'
git check-ignore .env secrets/
git status --short
git add .
git diff --cached --name-only
git diff --cached --check
```

La lista preparada no debe contener `.env`, `secrets/`, contraseñas, bases de datos ni resultados de compilación. Revisa también los valores de `k8s/` si los has personalizado. Si ya existe un repositorio, conserva su historia y su remoto: utiliza `git status` y `git remote -v` antes de seguir.

```powershell
git commit -m "Implement HexaOrders with hexagonal architecture and Docker Compose"
```

## 2. Crear y subir el remoto

Autentícate mediante el navegador y crea un repositorio privado en tu cuenta. Sustituye `TU_USUARIO`:

```powershell
gh auth login --hostname github.com --git-protocol https --web
gh repo create TU_USUARIO/hexaorders --private --source=. --remote=origin --push
gh repo view --web
```

Comparte acceso con tu profesor desde la configuración del repositorio. Para una entrega pública deliberada, usa `--public` en lugar de `--private`. El comando `gh repo create` crea el remoto y publica el commit; la documentación oficial explica ambos efectos. [GitHub CLI](https://cli.github.com/manual/gh_repo_create).

Si creaste antes un repositorio **vacío** desde la web, utiliza esta alternativa en lugar de `gh repo create`:

```powershell
git remote add origin https://github.com/TU_USUARIO/hexaorders.git
git push -u origin main
```

## 3. Actualizaciones posteriores

```powershell
git status --short
git add .
git diff --cached --name-only
git diff --cached --check
git commit -m "Describe the verified change"
git push
```

GitHub CLI y Docker Compose V2 son suficientes para este flujo. Un asistente de programación es opcional para editar el proyecto; no es una dependencia del despliegue.
