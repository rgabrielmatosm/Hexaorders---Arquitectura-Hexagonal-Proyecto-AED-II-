# Demostración de Redis: caché del catálogo, TTL y degradación

## 1. Qué hay realmente implementado

| Elemento | Código | Detalle |
| --- | --- | --- |
| Puerto | `application/port/out/CatalogCache.java` | `categories(loader)`, `activeProducts(categoryId, loader)`, `product(id, loader)`, `invalidate()`; incluye `CatalogCache.NONE` (sin caché) |
| Caso de uso | `application/service/CatalogService.java` | `categories()` → `cache.categories(catalog::categories)`; invalida tras cambios del administrador |
| Adaptador | `adapter/out/cache/RedisCatalogCache.java` | *Read-through* con `StringRedisTemplate` y JSON |
| Composición | `config/IntegrationConfiguration.java` | `RedisCatalogCache` si `polymer.integrations.redis.enabled=true` (modo full) |
| TTL | `@Value("${polymer.cache.catalog-ttl:60s}")` | **60 s** en producción (no se cambia) |
| Back-off | `@Value("${polymer.cache.backoff:30s}")` | Tras un error, omite Redis 30 s |
| Métrica | `polymer_cache_requests_total{cache="catalog",result=hit|miss|error|bypass}` | Más `polymer_cache_invalidations_total` |

(Rutas relativas a `services/order-api/src/main/java/com/polymer/`.)

**Claves reales**

- `polymer:catalog:version` → contador de versión. `invalidate()` lo incrementa.
- `polymer:catalog6:v<versión>:categories` → árbol de categorías.
- `polymer:catalog6:v<versión>:products:all` y `polymer:catalog6:v<versión>:products:<idCategoría>` → listados.
- `polymer:catalog6:v<versión>:product:<id>` → ficha de producto.

**TTL, LRU e invalidación no son lo mismo**

- **TTL** (*time to live*): cada clave caduca sola a los 60 s (`SET … EX`). Garantiza que un dato
  cambiado fuera de la aplicación no se sirva más de un minuto.
- **LRU** (*least recently used*): política de **expulsión** cuando Redis llega a su límite de memoria.
  Expulsa primero lo menos usado aunque no haya caducado. En POLYMER está configurada en
  `docker-compose.yml`: `redis-server --maxmemory 48mb --maxmemory-policy allkeys-lru` (y el contenedor
  tiene `mem_limit: 96m`). Protege la memoria; no decide la frescura de los datos (eso lo hacen el TTL y
  la invalidación).
- **Invalidación:** borrado **explícito** cuando el dato cambia. Aquí se hace por **versión**: al cambiar
  un producto, una promoción o el stock, `invalidate()` incrementa `polymer:catalog:version`. Todas las
  claves nuevas usan la versión nueva y las viejas caducan por TTL. No hace falta buscar ni borrar claves.

## 2. Demostración automatizada (con evidencias)

Con POLYMER en marcha:

```powershell
.\polymer.cmd demo -Scenario redis
```

Pasos que ejecuta y **comprueba** (falla si alguno no se cumple):

1. Lee la versión (`redis-cli GET polymer:catalog:version`) y calcula la clave
   `polymer:catalog6:v<versión>:categories`.
2. Borra **solo esa clave de caché** (`DEL`, nunca `FLUSHALL`): equivale a que expire. No borra datos.
3. `GET /api/catalog/categories` → **miss**: `polymer_cache_requests_total{result="miss"}` aumenta y la
   clave aparece en Redis con `TTL` > 0.
4. Segunda consulta → **hit**: aumenta `result="hit"`.
5. Espera a que `TTL` devuelva `-2` (clave expirada).
6. Nueva consulta → **miss** otra vez y entrada nueva con TTL completo.
7. Muestra las mismas series en Prometheus: `sum by (result) (polymer_cache_requests_total)`.

Las métricas se leen del endpoint interno de `order-api`
(`docker compose exec order-api curl -s http://127.0.0.1:9090/actuator/prometheus`), que es el mismo que
recoge Prometheus cada 10 s.

### TTL corto solo para la exposición

El TTL de producción es 60 s y **no se cambia**. Si en la exposición no quieres esperar un minuto:

```powershell
.\polymer.cmd demo-on -DemoTtl 20
```

```powershell
.\polymer.cmd demo -Scenario redis
```

```powershell
.\polymer.cmd demo-off
```

`demo-on` recrea **solo** `order-api` con `infra/demo/compose.demo.yml`
(`POLYMER_CACHE_CATALOG_TTL=20s`); `demo-off` vuelve a 60 s. Ningún dato cambia.

## 3. Comandos manuales (para enseñarlos en la terminal)

```powershell
docker compose exec redis redis-cli GET polymer:catalog:version
```

```powershell
docker compose exec redis redis-cli --scan --pattern "polymer:catalog6:*"
```

```powershell
docker compose exec redis redis-cli TTL polymer:catalog6:v0:categories
```

(Sustituye `v0` por la versión real que devuelve el primer comando.)

```powershell
docker compose exec order-api curl -s http://127.0.0.1:9090/actuator/prometheus | Select-String polymer_cache_requests_total
```

Prometheus (http://127.0.0.1:9091 → Graph): `sum by (result) (rate(polymer_cache_requests_total[1m]))`.

## 4. Degradación: Redis detenido

```powershell
.\polymer.cmd demo -Scenario redis-caida
```

1. `docker compose stop redis`.
2. El catálogo responde igual desde PostgreSQL (lo comprueba y mide el tiempo).
3. Aumentan `result="error"` y `result="bypass"`: el adaptador registra el error y omite Redis 30 s para
   no penalizar cada petición.
4. `docker compose up -d --wait redis` y, sin reiniciar `order-api`, vuelven los `hit`.

La degradación también se comprueba en cada ejecución del CI (`polymer.ps1 verify`, paso 7b:
«Sin Redis el catálogo se sirve desde PostgreSQL (respuesta < 5 s)» y «El catálogo vuelve a usar Redis sin
intervención manual»).

## 5. Grafana

Dashboard **POLYMER — Platform Integrations** (`infra/grafana/dashboards/polymer-integrations.json`):
paneles «Aciertos de caché del catálogo (5 min)» y «Caché Redis del catálogo por resultado / s», alimentados
por `polymer_cache_requests_total`. Usuario `admin`; contraseña en `secrets\grafana_password.txt`.

## 6. Posibles errores

| Síntoma | Causa | Solución |
| --- | --- | --- |
| «El servicio redis no está en marcha» | Modo `basic` o Redis detenido | `.\polymer.cmd start` (modo full) |
| Siempre `bypass` | Hubo un error hace menos de 30 s | Espera 30 s (back-off) |
| La clave no aparece | La versión cambió (un administrador editó el catálogo) | Repite el escenario: recalcula la clave |
| La espera del TTL es larga | TTL normal de 60 s | `.\polymer.cmd demo-on -DemoTtl 20` |
