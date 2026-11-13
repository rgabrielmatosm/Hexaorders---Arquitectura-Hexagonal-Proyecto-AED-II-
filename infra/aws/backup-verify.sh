#!/usr/bin/env bash
# POLYMER — verifica una copia de seguridad RESTAURÁNDOLA en una base TEMPORAL (polymer_restore_check)
# del mismo PostgreSQL y comparando recuentos con la base real. Nunca toca la base «orders».
# Uso: bash infra/aws/backup-verify.sh /var/backups/polymer/polymer-orders-AAAAMMDDTHHMMSSZ.dump
set -euo pipefail
cd "$(dirname "$0")/../.."
FILE="${1:?Indica el archivo .dump}"
sha256sum -c "$FILE.sha256"
TEMP=polymer_restore_check
psql() { docker compose exec -T db psql -U orders -v ON_ERROR_STOP=1 "$@"; }
psql -d postgres -c "DROP DATABASE IF EXISTS $TEMP;" -c "CREATE DATABASE $TEMP;"
docker compose exec -T db pg_restore -U orders -d "$TEMP" --no-owner --exit-on-error < "$FILE"
Q="SELECT (SELECT count(*) FROM orders)||'/'||(SELECT count(*) FROM app_users)||'/'||(SELECT count(*) FROM products)||'/'||(SELECT max(version::int) FROM flyway_schema_history)"
RESTORED=$(psql -d "$TEMP" -tAc "$Q")
CURRENT=$(psql -d orders -tAc "$Q")
echo "Copia restaurada (pedidos/usuarios/productos/migración): $RESTORED"
echo "Base actual                                          : $CURRENT"
psql -d postgres -c "DROP DATABASE $TEMP;"
echo "Verificación terminada: la copia se restaura sin errores."
