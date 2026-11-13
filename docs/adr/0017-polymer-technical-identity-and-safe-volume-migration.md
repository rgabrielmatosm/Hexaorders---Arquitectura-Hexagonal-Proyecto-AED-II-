# ADR 0017 — Identidad técnica POLYMER y migración segura de volúmenes

- **Estado:** aceptada
- **Contexto:**
  - El proyecto debía llamarse **POLYMER — Sistema de Gestión de Pedidos de Productos Tecnológicos** en toda su identidad, no solo en la interfaz (ADR 0016).
  - Las instalaciones existentes guardan sus datos en volúmenes de Docker del proyecto anterior (`hexaorders_postgres_data`, `hexaorders_grafana_data`…).
  - Docker **no permite renombrar** un volumen, y cambiar el nombre del proyecto de Compose crea volúmenes **vacíos**: parecería que se perdieron los datos.
  - Además, el ZIP del proyecto no incluye `secrets\` (las contraseñas no se distribuyen). Al descomprimirlo en otra carpeta, no se encontraban las contraseñas con las que se inicializaron esos volúmenes.

## Decisión

1. **Identidad técnica POLYMER:**
   - paquete Java `com.polymer`;
   - proyecto de Compose `polymer`, con contenedores `polymer-<servicio>-1` e imágenes `polymer/*`;
   - volúmenes `polymer_*`;
   - métricas `polymer_*` y dashboards `polymer*`;
   - etiqueta de logs `com.polymer.logs`, prefijo de configuración `polymer.*` y caché `polymer:*`;
   - claves del navegador `polymer.*`;
   - lanzador `polymer.cmd` / `tools/polymer.ps1` y namespace de Kubernetes `polymer`.

   El cambio se hizo con un script reproducible: `tools/migration/rename_to_polymer.py`.
2. **Migración de datos por copia verificada, nunca por renombrado.** `polymer.cmd start` (o `migrate`) hace, para cada volumen `hexaorders_*` sin su equivalente `polymer_*`:
   1. detener (sin borrar) los contenedores del proyecto anterior;
   2. crear `polymer_*`;
   3. copiar con `cp -a` desde un contenedor, con el origen montado en solo lectura;
   4. comparar la lista de sumas SHA-256 de todos los archivos del origen y del destino.

   - Si algo falla, solo se elimina la copia incompleta recién creada y no se arranca nada.
   - Los volúmenes originales **se conservan intactos** como copia de seguridad.
   - Se deja un registro en `polymer-migration.log`.
3. **Recuperación de contraseñas sin inventarlas:**
   - Si hay datos de una instalación anterior y faltan sus contraseñas, `start` busca la carpeta `secrets\` anterior junto al proyecto (o la indicada con `-SecretsFrom`).
   - Pide confirmación y copia **solo** los archivos que faltan.
   - Nunca sobrescribe ni muestra contraseñas.
   - Si no encuentra la carpeta, se detiene con instrucciones: nunca genera contraseñas nuevas para datos existentes.
4. **Identificadores heredados conservados a propósito** (ligados a datos persistentes):
   - **Migraciones Flyway V1–V8 y el generador de V7:** Flyway valida su suma de comprobación.
   - **RabbitMQ:** el usuario `hexaorders` y el exchange y las colas `hexaorders.*`, guardados en su volumen con los mensajes pendientes.
   - **Volúmenes `hexaorders_*`:** copia de seguridad.
   - **Alias `hexaorders.cmd`.**
5. **Datos de demostración:**
   - La migración **V9** actualiza el nombre visible y el correo de las seis cuentas de demostración, solo si conservan el valor original, y el modelo del ultrabook de la marca propia.
   - No toca contraseñas, roles, pedidos ni cuentas creadas por personas.

## Consecuencias

- **Arranque desde la carpeta nueva:**
  - Una instalación HexaOrders v6 arranca como POLYMER desde la carpeta nueva con `.\polymer.cmd start`, conservando pedidos, usuarios, métricas y dashboards.
  - Lo comprueba en el CI el job `migrate-from-v6`, que parte de HexaOrders v6 con 13 contenedores, datos y contraseñas aleatorias.
- **Espacio en disco:** la migración ocupa el doble mientras se conservan los originales. Borrar los volúmenes `hexaorders_*` es una decisión manual del usuario, documentada y nunca automática.
- **Prometheus:** la historia de métricas anterior a la migración queda guardada con los nombres `hexaorders_*`; los dashboards muestran las series `polymer_*` desde la migración.
