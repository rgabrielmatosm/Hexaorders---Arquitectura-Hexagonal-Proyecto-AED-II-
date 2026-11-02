-- V3 · Catálogo inicial (datos de demostración persistidos; editables desde la administración).
INSERT INTO categories (slug, name) VALUES
    ('tecnologia', 'Tecnología'),
    ('audio', 'Audio'),
    ('oficina', 'Oficina'),
    ('hogar', 'Hogar inteligente'),
    ('accesorios', 'Accesorios'),
    ('bienestar', 'Bienestar');

INSERT INTO products (sku, name, description, category_id, price, stock, hue)
SELECT v.sku, v.name, v.description, c.id, v.price, v.stock, v.hue
FROM (VALUES
    ('TEC-LAPTOP-14', 'Portátil Hexa 14"', 'Ultraligero de 1,2 kg, 16 GB de RAM y 512 GB SSD.', 'tecnologia', 1299.00, 12, 262),
    ('TEC-MONITOR-27', 'Monitor 27" QHD', 'Panel IPS 2560×1440 a 144 Hz con soporte ajustable.', 'tecnologia', 389.90, 20, 220),
    ('TEC-TABLET-11', 'Tablet 11" Pro', 'Pantalla de 120 Hz, 256 GB y lápiz incluido.', 'tecnologia', 649.00, 15, 280),
    ('TEC-DOCK-USBC', 'Dock USB-C 9 en 1', 'HDMI 4K, Ethernet, lector SD y carga de 100 W.', 'tecnologia', 89.50, 40, 200),
    ('AUD-HEADSET-NC', 'Auriculares con cancelación de ruido', 'Bluetooth 5.3, 40 h de batería y modo transparencia.', 'audio', 229.00, 25, 300),
    ('AUD-SPEAKER-MINI', 'Altavoz portátil Mini', 'Resistente al agua IP67 con 12 h de autonomía.', 'audio', 59.90, 60, 320),
    ('AUD-MIC-USB', 'Micrófono USB de estudio', 'Patrón cardioide y monitorización sin latencia.', 'audio', 119.00, 18, 340),
    ('OFI-CHAIR-ERGO', 'Silla ergonómica', 'Soporte lumbar ajustable y malla transpirable.', 'oficina', 349.00, 8, 160),
    ('OFI-DESK-LIFT', 'Escritorio elevable', 'Motor doble, memoria de 4 alturas, 140×70 cm.', 'oficina', 529.00, 5, 140),
    ('OFI-LAMP-LED', 'Lámpara LED de escritorio', 'Temperatura de color regulable y carga inalámbrica.', 'oficina', 45.00, 50, 50),
    ('HOG-CAM-WIFI', 'Cámara Wi-Fi 2K', 'Visión nocturna y detección de personas.', 'hogar', 69.90, 30, 190),
    ('HOG-PLUG-SMART', 'Enchufe inteligente (pack de 2)', 'Control por app y medición de consumo.', 'hogar', 24.90, 100, 175),
    ('HOG-HUB', 'Hub domótico', 'Compatible con Zigbee, Thread y Matter.', 'hogar', 99.00, 3, 210),
    ('ACC-BACKPACK', 'Mochila técnica 20 L', 'Compartimento acolchado para portátil de 16".', 'accesorios', 79.00, 35, 240),
    ('ACC-CHARGER-65', 'Cargador GaN 65 W', 'Tres puertos, carga rápida PD 3.0.', 'accesorios', 39.90, 80, 230),
    ('ACC-MOUSE-PRO', 'Ratón inalámbrico Pro', 'Sensor de 8000 DPI y conexión multidispositivo.', 'accesorios', 69.00, 4, 250),
    ('BIE-WATCH-FIT', 'Reloj deportivo', 'GPS, frecuencia cardíaca y 10 días de batería.', 'bienestar', 189.00, 22, 150),
    ('BIE-BOTTLE', 'Botella térmica 750 ml', 'Acero inoxidable, 24 h de frío y 12 h de calor.', 'bienestar', 24.00, 120, 170)
) AS v (sku, name, description, slug, price, stock, hue)
JOIN categories c ON c.slug = v.slug;
