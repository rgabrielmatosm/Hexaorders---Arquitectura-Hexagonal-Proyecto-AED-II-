-- POLYMER: identidad en los datos de demostración. Migración NO destructiva y acotada:
--  * solo toca las seis cuentas de demostración cuando conservan EXACTAMENTE el valor original
--    (nombre visible o correo); si el administrador los cambió, se respetan;
--  * nunca toca contraseñas, roles, pedidos ni cuentas creadas por personas;
--  * el modelo del ultrabook de la marca propia deja de llevar el nombre anterior.

UPDATE app_users SET display_name = 'Administración Polymer'
 WHERE username = 'admin' AND display_name = 'Administración ' || 'Hexa' || 'Orders';

UPDATE app_users SET email = username || '@polymer.local'
 WHERE username IN ('admin', 'operador', 'logistica', 'logistica2', 'observador', 'cliente')
   AND email = username || '@' || 'hexa' || 'orders.local'
   AND NOT EXISTS (SELECT 1 FROM app_users other WHERE other.email = app_users.username || '@polymer.local');

UPDATE products SET model = 'Book 14 (2024)', updated_at = now()
 WHERE sku = 'TEC-LAPTOP-14' AND model = 'Hexa' || ' 14 (2024)';
