# Despliegue reproducible en una VM

Elige **una** VM Ubuntu 24.04 LTS amd64. El ejemplo utiliza Google Cloud; después de entrar por SSH, la instalación y los comandos Compose son los mismos en una VM Ubuntu de AWS o Azure. El único archivo del proyecto que necesitas copiar es `docker-compose.yml`. Los secretos se generan en la VM.

Este despliegue universitario usa un único host: los volúmenes persisten reinicios de contenedores, pero no proporcionan alta disponibilidad ni copias externas. La construcción inicial descarga imágenes y dependencias de Maven/Python; necesita salida a Internet. Como punto de partida para la demo usa 2 vCPU, 8 GB RAM y 30 GB de disco; mide consumo antes de dimensionar un entorno real. Google documenta esos recursos para `e2-standard-2`. [Tipos E2](https://docs.cloud.google.com/compute/docs/general-purpose-machines#e2_machine_types).

## 1. Crear la VM en Google Cloud

Estos comandos son para **Bash** en tu equipo con Google Cloud CLI configurado, o en Cloud Shell. Si utilizas Cloud Shell, `ADMIN_CIDR` debe corresponder a la IP del equipo desde el que abrirás SSH. Reemplaza los dos valores indicados; los comandos de esta sección crean recursos facturables cuando tú los ejecutes.

```bash
gcloud auth login
export PROJECT_ID='TU_PROJECT_ID'
export ZONE='us-central1-a'
export ADMIN_CIDR='TU_IP_PUBLICA/32'
gcloud config set project "$PROJECT_ID"
gcloud services enable compute.googleapis.com
gcloud compute networks create hexaorders-vpc --subnet-mode=auto
gcloud compute firewall-rules create hexaorders-ssh \
  --network=hexaorders-vpc --direction=INGRESS \
  --action=ALLOW --rules=tcp:22 \
  --source-ranges="$ADMIN_CIDR" --target-tags=hexaorders-ssh
gcloud compute instances create hexaorders-demo \
  --zone="$ZONE" --machine-type=e2-standard-2 \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-balanced \
  --network=hexaorders-vpc --tags=hexaorders-ssh \
  --no-service-account --no-scopes
```

La VPC dedicada recibe únicamente la regla SSH indicada. El ejemplo no añade reglas públicas para 8080, 3000, 9091 o 5432. Necesitas proyecto con facturación activa, permisos Compute/redes y cuota en la zona. Si cambia tu IP, actualiza la regla SSH. [Crear instancias](https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/create), [firewall](https://docs.cloud.google.com/sdk/gcloud/reference/compute/firewall-rules/create), [imagen Ubuntu](https://docs.cloud.google.com/compute/docs/images/os-details#ubuntu).

Desde la carpeta que contiene el YAML, usando **el mismo equipo cuya IP autorizaste**:

```bash
gcloud compute scp docker-compose.yml hexaorders-demo:~/docker-compose.yml --zone="$ZONE"
gcloud compute ssh hexaorders-demo --zone="$ZONE"
```

Si creaste recursos desde Cloud Shell y te conectarás desde tu PC, ejecuta allí primero `gcloud auth login`, configura el mismo proyecto y define `ZONE=us-central1-a`. [Transferir con SCP](https://docs.cloud.google.com/sdk/gcloud/reference/compute/scp).

## 2. Instalar Docker Engine y Compose plugin

Dentro de una **VM nueva Ubuntu 24.04**, ejecuta Bash. Este procedimiento utiliza el repositorio APT oficial de Docker:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl openssl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker compose version
sudo docker run --rm hello-world
```

Exige **Docker Compose >= 2.23.1**, porque el entregable incorpora `configs.content`; también utiliza BuildKit para los Dockerfiles en línea. Docker Desktop reciente permite el mismo Compose en tu máquina local. [Instalación oficial](https://docs.docker.com/engine/install/ubuntu/), [configs.content](https://docs.docker.com/reference/compose-file/configs/), [dockerfile_inline](https://docs.docker.com/reference/compose-file/build/#dockerfile_inline).

## 3. Generar secretos y arrancar

Dentro de la VM, mueve el YAML a su directorio estable y genera secretos una sola vez. Conserva `.env`: PostgreSQL mantiene sus credenciales dentro del volumen existente y cambiar este archivo por sí solo no rota la contraseña de una base ya inicializada.

```bash
mkdir -p ~/hexaorders
mv ~/docker-compose.yml ~/hexaorders/docker-compose.yml
cd ~/hexaorders
umask 077
if [ ! -e .env ]; then
  printf 'DB_PASSWORD=%s\nGRAFANA_PASSWORD=%s\n' \
    "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" > .env
fi
chmod 600 .env
set -a
. ./.env
set +a
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose config --quiet
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose build --pull
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose up -d --wait --wait-timeout 240
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose ps
```

Las variables se exportan explícitamente para alimentar `secrets.environment`; `sudo --preserve-env` las conserva al ejecutar Compose. No se incluyen en el YAML entregable ni en Git. La construcción se ejecuta antes de `up`, por lo que el tiempo máximo de espera corresponde al arranque. [Compose secrets](https://docs.docker.com/reference/compose-file/secrets/), [compose up](https://docs.docker.com/reference/cli/docker/compose/up/).

Comprueba una operación desde la VM:

```bash
curl -fsS -X POST http://127.0.0.1:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"demo-001","amount":125.50}'
curl -fsS http://127.0.0.1:8080/api/orders
```

El pedido de importe 125.50 debe quedar aprobado con score 10. Un pedido de importe 1500 debe quedar en revisión con score 75. Si `up --wait` falla, inspecciona `docker compose ps` y `docker compose logs --tail=100` usando el mismo prefijo `sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD`.

## 4. Ver la aplicación y Grafana mediante túnel SSH

Desde una **segunda terminal de tu equipo**, conserva abierta esta sesión. Las aplicaciones están publicadas en la interfaz loopback de la VM:

```bash
gcloud compute ssh hexaorders-demo --zone=us-central1-a -- \
  -N -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:8080:127.0.0.1:8080 \
  -L 127.0.0.1:3000:127.0.0.1:3000 \
  -L 127.0.0.1:9091:127.0.0.1:9091
```

Abre [aplicación/API](http://127.0.0.1:8080), [Grafana](http://127.0.0.1:3000/d/hexaorders) y [Prometheus](http://127.0.0.1:9091) en tu navegador local. Grafana utiliza `admin` y el valor de `GRAFANA_PASSWORD` generado en la VM. Consulta `.env` en tu sesión privada cuando necesites iniciar sesión. Si alguno de los puertos locales está ocupado, cambia únicamente el primer número del reenvío correspondiente. [Reenvío SSH con gcloud](https://docs.cloud.google.com/sdk/gcloud/reference/compute/ssh).

## 5. Reinicio, actualización y conservación de datos

En cada nueva sesión, entra a `~/hexaorders` y vuelve a exportar `.env` antes de ejecutar Compose. `restart: unless-stopped` permite reiniciar servicios con Docker; un contenedor parado explícitamente permanece parado.

```bash
cd ~/hexaorders
set -a
. ./.env
set +a
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose logs --tail=100
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose down
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose up -d --wait --wait-timeout 240
```

`down` conserva los volúmenes por defecto. `down -v` elimina los datos y debe reservarse para un reinicio deliberado de la demostración. Copia de seguridad mínima de pedidos:

```bash
umask 077
sudo --preserve-env=DB_PASSWORD,GRAFANA_PASSWORD docker compose exec -T db \
  pg_dump -U orders -d orders > "orders-$(date +%Y%m%d-%H%M%S).sql"
```

Al acabar la presentación puedes detener la VM con `gcloud compute instances stop hexaorders-demo --zone=us-central1-a`. El disco y otros recursos asociados pueden seguir generando cargos; elimina deliberadamente los recursos que ya no necesites tras conservar lo que quieras recuperar.

## AWS o Azure

Crea una VM Ubuntu 24.04 amd64 con recursos equivalentes y una regla de entrada SSH/22 limitada a tu IP. En AWS es una regla del security group; en Azure, del NSG. Copia el YAML con SCP y ejecuta los pasos 2 y 3. Para el túnel utiliza el SSH estándar de tu proveedor con los mismos `-L` del paso 4. Docker Compose sigue siendo el único orquestador de este despliegue; los manifiestos `k8s/` son el valor agregado opcional y requieren otro flujo.
