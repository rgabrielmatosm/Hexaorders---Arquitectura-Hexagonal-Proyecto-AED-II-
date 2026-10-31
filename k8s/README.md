# Adaptación básica a Kubernetes

Esta carpeta es un valor agregado opcional. El despliegue principal y el archivo que entregas siguen siendo `docker-compose.yml`; Kubernetes utiliza otro orquestador y no es necesario para ejecutar la entrega con Compose.

Se mantienen los mismos dos hexágonos y sus contratos HTTP. Kubernetes cambia el empaquetado operativo: Services para DNS, Deployments para aplicaciones, StatefulSet para PostgreSQL, PVC para persistencia y Secret/ConfigMap para configuración.

## Recursos y prerrequisitos

| Archivo | Recursos |
| --- | --- |
| `00-namespace.yaml` | Namespace `hexaorders`. |
| `10-monitoring-config.yaml` | ConfigMap `monitoring-config`, generado desde los archivos de `infra/` por `tools/generate_delivery.py`. |
| `20-data.yaml` | PostgreSQL 17.11, Service headless `db`, StatefulSet de una réplica y PVC de 1 GiB. |
| `30-apps.yaml` | Java `order-api` y Python `risk-engine`, Services y Deployments de una réplica. |
| `40-observability.yaml` | Prometheus y Grafana con Services internos, Deployments y PVC de 2 y 1 GiB. |

Necesitas `kubectl`, un clúster Linux amd64 compatible con `apps/v1`, DNS y una **StorageClass predeterminada con aprovisionador dinámico**. Los PVC omiten `storageClassName` para usar esa clase. Comprueba `kubectl get storageclass`; un PVC `Pending` suele indicar que falta almacenamiento o capacidad. Los volúmenes deben admitir permisos de grupo `fsGroup`. [Persistent Volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/).

Las imágenes propias se construyen con Compose. En un clúster remoto deben estar en un registry accesible; en kind se pueden cargar desde tu Docker local. Comprueba siempre el contexto antes de aplicar recursos:

```bash
kubectl config current-context
kubectl get nodes
kubectl get storageclass
```

## 1. Construir y cargar las imágenes en kind

Ejemplo en **Bash**, desde la raíz del proyecto, con Docker, Compose >= 2.23.1, kind y kubectl instalados. Genera primero los archivos de entrega si has modificado fuentes: `python tools/generate_delivery.py`. Prepara las variables de Compose tal como indica el README principal.

```bash
docker compose build --pull order-api risk-engine
kind create cluster --name hexaorders
kind load docker-image hexaorders/order-api:1.0.0 hexaorders/risk-engine:1.0.0 --name hexaorders
kubectl config use-context kind-hexaorders
```

Los manifiestos utilizan `imagePullPolicy: IfNotPresent`, por lo que kind aprovecha las imágenes cargadas en sus nodos. Después de reconstruir una etiqueta local debes repetir `kind load docker-image` y reiniciar los Deployments. Para entregas remotas utiliza etiquetas nuevas o digests. [Guía oficial de kind](https://kind.sigs.k8s.io/docs/user/quick-start/).

## 2. Crear secretos fuera del repositorio

Los valores sensibles no aparecen en ningún manifiesto versionado. En esta terminal se crean archivos en un directorio temporal del sistema y se envían al API de Kubernetes. Conserva ese directorio durante la demostración para consultar la contraseña de Grafana:

```bash
kubectl apply -f k8s/00-namespace.yaml
umask 077
SECRET_DIR="$(mktemp -d -t hexaorders-secrets.XXXXXX)"
printf '%s' "$(openssl rand -hex 24)" > "$SECRET_DIR/db_password"
printf '%s' "$(openssl rand -hex 24)" > "$SECRET_DIR/grafana_password"
kubectl -n hexaorders create secret generic app-secrets \
  --from-file=db_password="$SECRET_DIR/db_password" \
  --from-file=grafana_password="$SECRET_DIR/grafana_password"
```

En despliegues posteriores reutiliza el Secret existente. Cambiar su contraseña no rota automáticamente la de PostgreSQL ya inicializado ni la cuenta admin almacenada por Grafana. Los volúmenes Secret se montan como archivos de lectura y cada servicio recibe exclusivamente la clave que necesita. La protección del almacén de secretos del clúster depende de su configuración y permisos.

## 3. Aplicar y esperar disponibilidad

Primero se espera la base de datos para facilitar la demostración inicial; Kubernetes no interpreta `depends_on` de Compose. Si un proceso arranca antes que su dependencia y falla, el controlador lo reinicia; las sondas de readiness impiden enviarle tráfico hasta que esté listo.

```bash
kubectl apply -f k8s/10-monitoring-config.yaml
kubectl apply -f k8s/20-data.yaml
kubectl -n hexaorders rollout status statefulset/db --timeout=300s
kubectl apply -f k8s/30-apps.yaml
kubectl apply -f k8s/40-observability.yaml
kubectl -n hexaorders rollout status deployment/risk-engine --timeout=180s
kubectl -n hexaorders rollout status deployment/order-api --timeout=300s
kubectl -n hexaorders rollout status deployment/prometheus --timeout=180s
kubectl -n hexaorders rollout status deployment/grafana --timeout=180s
kubectl -n hexaorders get pods,services,pvc
```

Java usa el puerto de gestión **9090** para sus sondas: liveness comprueba el proceso y readiness incluye PostgreSQL. El endpoint de negocio sigue siendo 8080. Python usa `/health/live` y `/health/ready` en 8000. Las sondas startup conceden tiempo de arranque antes de activar liveness. [Sondas Kubernetes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/).

Las aplicaciones se ejecutan como UID 10001, Prometheus como 65534, Grafana como 472 y PostgreSQL como 999, sin escalada de privilegios ni capabilities. El sistema raíz es de lectura; las rutas de datos y temporales son volúmenes explícitos. `fsGroup` permite escribir los volúmenes persistentes. PostgreSQL tiene 128 MiB de memoria compartida y `PGDATA` en un subdirectorio del PVC. [Security context](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/).

## 4. Abrir la demostración

Ejecuta cada port-forward en una terminal independiente y mantenla abierta:

```bash
kubectl -n hexaorders port-forward --address=127.0.0.1 service/order-api 8080:8080
```

```bash
kubectl -n hexaorders port-forward --address=127.0.0.1 service/grafana 3000:3000
```

```bash
kubectl -n hexaorders port-forward --address=127.0.0.1 service/prometheus 9091:9090
```

Abre [aplicación](http://127.0.0.1:8080), [Grafana](http://127.0.0.1:3000/d/hexaorders) y [Prometheus](http://127.0.0.1:9091). Grafana usa `admin`; consulta su contraseña con `cat "$SECRET_DIR/grafana_password"` en la terminal donde la generaste. No hay LoadBalancer, NodePort ni Ingress público.

```bash
curl -fsS -X POST http://127.0.0.1:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"demo-001","amount":125.50}'
```

## Alternativa: registry para un clúster remoto

Publica en un namespace de registry que te pertenezca. Las etiquetas predeterminadas `hexaorders/*` sirven para uso local; no debes asumir permiso para publicarlas en Docker Hub. Este ejemplo crea un override Compose temporal y publica en GHCR después de autenticarte mediante `docker login ghcr.io` con tus credenciales y permisos de escritura. Para imágenes públicas no hacen falta credenciales de lectura en el clúster.

```bash
export REGISTRY_OWNER='TU_USUARIO_EN_MINUSCULAS'
OVERRIDE_FILE="$(mktemp -t hexaorders-registry.XXXXXX)"
cat > "$OVERRIDE_FILE" <<EOF
services:
  order-api:
    image: ghcr.io/${REGISTRY_OWNER}/hexaorders-order-api:1.0.0
  risk-engine:
    image: ghcr.io/${REGISTRY_OWNER}/hexaorders-risk-engine:1.0.0
EOF
docker login ghcr.io
docker compose -f docker-compose.yml -f "$OVERRIDE_FILE" build --pull order-api risk-engine
docker compose -f docker-compose.yml -f "$OVERRIDE_FILE" push order-api risk-engine
```

Con el contexto remoto seleccionado, crea namespace/Secret y aplica los manifiestos según los pasos anteriores. Cambia las imágenes de ambos Deployments inmediatamente después de aplicar `30-apps.yaml`, antes de esperar su rollout:

```bash
kubectl -n hexaorders set image deployment/order-api \
  order-api="ghcr.io/${REGISTRY_OWNER}/hexaorders-order-api:1.0.0"
kubectl -n hexaorders set image deployment/risk-engine \
  risk-engine="ghcr.io/${REGISTRY_OWNER}/hexaorders-risk-engine:1.0.0"
kubectl -n hexaorders rollout status deployment/order-api --timeout=300s
kubectl -n hexaorders rollout status deployment/risk-engine --timeout=180s
```

Si el registry es privado, configura primero un `imagePullSecret` y referencia ese secreto en `spec.template.spec.imagePullSecrets` de ambos Deployments. Un `kubectl apply` posterior del manifiesto original restaura sus imágenes locales; incorpora tu referencia de registry al manifiesto de tu entorno antes de automatizar cambios.

## Observabilidad, cambios y límites

El ConfigMap reutiliza los destinos DNS `order-api:9090` y `risk-engine:8000`, las reglas de alerta y el dashboard provisionado. Los archivos se montan mediante `subPath`: los cambios de ConfigMap requieren recrear los Pods consumidores. [ConfigMap y subPath](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/).

```bash
kubectl apply -f k8s/10-monitoring-config.yaml
kubectl -n hexaorders rollout restart deployment/prometheus deployment/grafana
```

Prometheus y Grafana usan estrategia `Recreate` para sus PVC `ReadWriteOnce`. La base es de una réplica y su StatefulSet mantiene identidad y volumen; **no es PostgreSQL de alta disponibilidad**. Los PVC persisten al eliminar Pods y pueden sobrevivir a eliminar el StatefulSet, según la política del clúster. [StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/).

Las redes `internal: true` de Compose no se convierten automáticamente en aislamiento de red Kubernetes. Estos manifiestos usan conectividad interna normal del clúster, sin NetworkPolicies. Un entorno de producción necesita políticas compatibles con su CNI, autenticación, TLS, backups externos y una estrategia de disponibilidad y actualización; esos componentes están fuera del alcance de esta adaptación básica.

Para diagnosticar:

```bash
kubectl -n hexaorders get events --sort-by=.lastTimestamp
kubectl -n hexaorders logs deployment/order-api --tail=100
kubectl -n hexaorders describe pod -l app=order-api
kubectl -n hexaorders get pvc
```

No se ha ejecutado un despliegue real de Kubernetes desde este proyecto: valida los manifiestos contra tu API con `kubectl apply --dry-run=server -f k8s/` después de preparar namespace y Secret. No elimines el namespace o el clúster de demostración hasta decidir qué datos quieres conservar.
