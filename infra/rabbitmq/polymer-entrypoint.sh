#!/bin/sh
# Lee la contraseña del secreto de Docker y genera la configuración de credenciales en un tmpfs
# (/etc/rabbitmq/conf.d), porque el sistema de archivos del contenedor es de solo lectura.
set -eu
secret=/run/secrets/rabbitmq_password
if [ ! -f "$secret" ]; then
  echo "Falta el secreto $secret (genera secrets\\rabbitmq_password.txt con .\\polymer.cmd secrets)" >&2
  exit 1
fi
password=$(tr -d '\r\n' < "$secret")
umask 027
mkdir -p /etc/rabbitmq/conf.d
printf 'default_user = %s\ndefault_pass = %s\ndefault_vhost = /\n' "${RABBITMQ_USER:-hexaorders}" "$password" > /etc/rabbitmq/conf.d/20-credentials.conf
chown rabbitmq:rabbitmq /etc/rabbitmq/conf.d/20-credentials.conf
exec docker-entrypoint.sh "$@"
