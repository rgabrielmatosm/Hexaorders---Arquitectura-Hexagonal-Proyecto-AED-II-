-- POLYMER (v7 de la interfaz): cambio de marca comercial. Migración NO destructiva:
--  * no borra filas ni columnas; no toca usuarios, contraseñas, pedidos ni líneas de pedido;
--  * la marca propia de los productos de demostración pasa de «HexaOrders» a «Polymer»;
--  * el tema de contacto CLAIM habilita el Libro de reclamaciones (página /libro-reclamaciones.html).
-- Los identificadores técnicos (paquetes, tablas, claves, números HX-…) no cambian.

ALTER TABLE products ALTER COLUMN brand SET DEFAULT 'Polymer';

UPDATE products SET brand = 'Polymer', updated_at = now() WHERE brand = 'HexaOrders';
UPDATE products SET name = replace(name, 'HexaOrders', 'Polymer'), updated_at = now() WHERE name LIKE '%HexaOrders%';
UPDATE products SET description = replace(description, 'HexaOrders', 'Polymer'), updated_at = now() WHERE description LIKE '%HexaOrders%';
UPDATE products SET long_description = replace(long_description, 'HexaOrders', 'Polymer'), updated_at = now() WHERE long_description LIKE '%HexaOrders%';

ALTER TABLE contact_messages DROP CONSTRAINT IF EXISTS contact_messages_topic_check;
ALTER TABLE contact_messages ADD CONSTRAINT contact_messages_topic_check
    CHECK (topic IN ('DEMO', 'SUPPORT', 'ACADEMIC', 'OTHER', 'ORDERS', 'WARRANTY', 'SALES', 'CLAIM'));
