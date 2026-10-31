# Motor de riesgo Python

Servicio HTTP interno y sin estado. `bootstrap.py` conecta el adaptador FastAPI
con el puerto de entrada `ScoreRiskPort`; `ScoreRisk` ejecuta la política pura del
dominio. `domain/` y `application/` importan solamente la biblioteca estándar y
capas internas. Una prueba AST verifica esa dirección de dependencias.

`POST /score` recibe `{"customerId":"client_01","amount":"1500.00"}` y devuelve
`{"score":75,"decision":"REVIEW"}`. También acepta un número JSON. El importe se
lee directamente como `Decimal`, sin conversión intermedia a punto flotante;
debe ser mayor que cero, como máximo 1000000 y tener hasta dos decimales. Los
identificadores cumplen `[A-Za-z0-9_-]{1,64}`. Un importe inválido genera HTTP 422.

Política demostrativa: importe >=1000 produce 75 puntos; >=500, 40; el resto, 10.
Desde 70 puntos la decisión es `REVIEW`. No es un modelo financiero real.

`/health/live`, `/health/ready` y `/metrics` están disponibles en el puerto 8000.
Las métricas cuentan solo evaluaciones válidas, con etiquetas de cardinalidad
acotada (`APPROVED` y `REVIEW`). Uvicorn usa un proceso por contenedor; para
escalar se agregan contenedores y Prometheus consulta cada instancia.

Pruebas íntegramente en Docker:

```bash
docker build --target test -t hexaorders-risk:test services/risk-engine
```

El build de la imagen final también obliga a ejecutar estas pruebas. La imagen
runtime usa UID/GID 10001, instala solo dependencias de producción y emplea
`urllib` para su healthcheck, sin agregar curl.
