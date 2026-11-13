#!/usr/bin/env bash
# POLYMER — copia de seguridad de PostgreSQL (pg_dump, formato custom) y, opcionalmente, subida a S3.
# Se ejecuta en la instancia, en la carpeta del proyecto. No detiene servicios ni modifica datos.
# Uso: bash infra/aws/backup.sh          (cron diario: ver docs/final/DESPLIEGUE_AWS_HTTPS.md)
# Variables opcionales: POLYMER_BACKUP_DIR (por defecto /var/backups/polymer),
#                       POLYMER_BACKUP_S3 (p. ej. s3://mi-bucket/polymer; requiere el rol IAM de la instancia),
#                       POLYMER_BACKUP_KEEP (copias locales a conservar, por defecto 14).
set -euo pipefail
cd "$(dirname "$0")/../.."
DIR="${POLYMER_BACKUP_DIR:-/var/backups/polymer}"
KEEP="${POLYMER_BACKUP_KEEP:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$DIR/polymer-orders-$STAMP.dump"
mkdir -p "$DIR"
umask 077
# pg_dump dentro del contenedor (socket local): la contraseña nunca aparece en la línea de comandos.
docker compose exec -T db pg_dump -U orders -d orders --format=custom --no-owner > "$FILE.partial"
mv "$FILE.partial" "$FILE"
sha256sum "$FILE" > "$FILE.sha256"
echo "Copia creada: $FILE ($(du -h "$FILE" | cut -f1))"
if [[ -n "${POLYMER_BACKUP_S3:-}" ]]; then
  aws s3 cp "$FILE" "$POLYMER_BACKUP_S3/" --sse AES256 --only-show-errors
  aws s3 cp "$FILE.sha256" "$POLYMER_BACKUP_S3/" --sse AES256 --only-show-errors
  echo "Subida a $POLYMER_BACKUP_S3"
fi
# Rotación local: conserva las $KEEP más recientes.
ls -1t "$DIR"/polymer-orders-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do rm -f "$old" "$old.sha256"; done
