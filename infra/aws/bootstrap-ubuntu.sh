#!/usr/bin/env bash
# POLYMER — preparación de una instancia EC2 Ubuntu 24.04 LTS (x86_64). Se ejecuta DENTRO de la instancia
# (no crea recursos de AWS). Instala Docker Engine + Compose, Nginx, certbot y PowerShell, y prepara la
# carpeta de copias de seguridad. Idempotente. Uso: sudo bash infra/aws/bootstrap-ubuntu.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then echo "Ejecuta con sudo" >&2; exit 1; fi

apt-get update
apt-get install -y ca-certificates curl gnupg nginx certbot python3 unattended-upgrades

# Docker Engine y plugin Compose desde el repositorio oficial de Docker.
install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.asc ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# PowerShell 7 (para tools/polymer.ps1: secretos, arranque, health, change-password).
if ! command -v pwsh >/dev/null; then snap install powershell --classic; fi

# Usuario que administra POLYMER (el que ejecuta este script con sudo).
if [[ -n "${SUDO_USER:-}" ]]; then usermod -aG docker "$SUDO_USER"; fi

# Nginx del host: snippet común y carpeta del desafío ACME.
install -d /var/www/certbot /etc/nginx/snippets
install -m 0644 "$(dirname "$0")/nginx-proxy-snippet.conf" /etc/nginx/snippets/polymer-proxy.conf
rm -f /etc/nginx/sites-enabled/default

# Copias de seguridad locales (sin acceso de otros usuarios).
install -d -m 0700 /var/backups/polymer

# Actualizaciones de seguridad automáticas del sistema operativo.
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "Listo. Cierra la sesión y vuelve a entrar para usar docker sin sudo."
