"""Datos del catálogo tecnológico de HexaOrders v6 (demostración).

Precios en soles (PEN), plausibles pero ficticios: no son ofertas reales del mercado. Las marcas y
modelos sirven para que la búsqueda, los filtros y las fichas técnicas sean realistas.

Formato de cada producto (una cadena):
    "Marca | Modelo | precio | precio anterior | Etiqueta=valor; Etiqueta=valor; ..."
El precio anterior es opcional (vacío). tools/generate_catalog.py convierte estos datos en la
migración V7 (idempotente) y deriva los filtros técnicos de las etiquetas de la ficha.
"""

# slug, nombre, tono (0-359) y subcategorías: slug, nombre, sustantivo del producto, frase comercial.
CATEGORIES = [
    ('computadoras', 'Computadoras y laptops', 255, [
        ('laptops-oficina', 'Laptops de oficina', 'Laptop', 'una laptop fiable para trabajar, estudiar y videollamadas todo el día'),
        ('laptops-gaming', 'Laptops gaming', 'Laptop gaming', 'una laptop gaming con gráfica dedicada y pantalla de alta frecuencia'),
        ('ultrabooks', 'Ultrabooks', 'Ultrabook', 'un ultrabook ligero y delgado con gran autonomía'),
        ('workstations', 'Workstations', 'Workstation móvil', 'una estación de trabajo certificada para diseño, ingeniería y ciencia de datos'),
        ('computadoras-escritorio', 'Computadoras de escritorio', 'PC de escritorio', 'una computadora de escritorio potente y ampliable'),
        ('mini-pc', 'Mini PC', 'Mini PC', 'un equipo compacto que ocupa muy poco espacio en el escritorio'),
        ('all-in-one', 'All-in-One', 'All-in-One', 'un equipo todo en uno con pantalla integrada y cableado mínimo'),
        ('equipos-empresariales', 'Equipos empresariales', 'Laptop empresarial', 'un equipo empresarial con seguridad, gestión remota y garantía extendida'),
        ('accesorios-laptop', 'Accesorios para laptops', 'Accesorio', 'un accesorio pensado para transportar y proteger tu laptop'),
    ]),
    ('componentes', 'Componentes de PC', 20, [
        ('procesadores-intel', 'Procesadores Intel', 'Procesador', 'un procesador Intel para equipos de escritorio'),
        ('procesadores-amd', 'Procesadores AMD', 'Procesador', 'un procesador AMD Ryzen para equipos de escritorio'),
        ('tarjetas-graficas-nvidia', 'Tarjetas gráficas NVIDIA', 'Tarjeta gráfica', 'una tarjeta gráfica NVIDIA GeForce para juegos y creación'),
        ('tarjetas-graficas-amd', 'Tarjetas gráficas AMD', 'Tarjeta gráfica', 'una tarjeta gráfica AMD Radeon para juegos en alta resolución'),
        ('placas-madre', 'Placas madre', 'Placa madre', 'una placa madre con buena alimentación y conectividad moderna'),
        ('memorias-ram', 'Memorias RAM', 'Memoria RAM', 'un kit de memoria para acelerar juegos y multitarea'),
        ('ssd-nvme', 'SSD NVMe', 'SSD NVMe', 'una unidad NVMe M.2 de alta velocidad'),
        ('ssd-sata', 'SSD SATA', 'SSD SATA', 'una unidad SSD SATA para renovar equipos con disco duro'),
        ('discos-duros', 'Discos duros', 'Disco duro', 'un disco duro interno de gran capacidad'),
        ('fuentes-poder', 'Fuentes de poder', 'Fuente de poder', 'una fuente certificada con protecciones completas'),
        ('refrigeracion-aire', 'Refrigeración por aire', 'Disipador', 'un disipador por aire silencioso y eficiente'),
        ('refrigeracion-liquida', 'Refrigeración líquida', 'Refrigeración líquida', 'un sistema de refrigeración líquida todo en uno'),
        ('gabinetes', 'Gabinetes', 'Gabinete', 'un gabinete con buen flujo de aire y montaje sencillo'),
        ('ventiladores', 'Ventiladores', 'Ventiladores', 'un pack de ventiladores para mejorar el flujo de aire'),
        ('accesorios-ensamblaje', 'Accesorios de ensamblaje', 'Accesorio de ensamblaje', 'un accesorio útil para montar y mantener tu PC'),
    ]),
    ('monitores', 'Monitores y pantallas', 200, [
        ('monitores-full-hd', 'Monitores Full HD', 'Monitor', 'un monitor Full HD para oficina y uso diario'),
        ('monitores-qhd', 'Monitores QHD', 'Monitor', 'un monitor QHD con más espacio de trabajo y detalle'),
        ('monitores-4k', 'Monitores 4K', 'Monitor', 'un monitor 4K para edición de fotos, video y productividad'),
        ('monitores-gaming', 'Monitores gaming', 'Monitor gaming', 'un monitor gaming de alta frecuencia y baja latencia'),
        ('monitores-ultrawide', 'Monitores ultrawide', 'Monitor ultrawide', 'un monitor panorámico 21:9 para multitarea e inmersión'),
        ('monitores-profesionales', 'Monitores profesionales', 'Monitor profesional', 'un monitor calibrado de fábrica para trabajo de color'),
        ('monitores-portatiles', 'Monitores portátiles', 'Monitor portátil', 'un monitor portátil USB-C para trabajar en cualquier lugar'),
        ('soportes-monitor', 'Soportes para monitores', 'Soporte', 'un soporte ergonómico para colocar la pantalla a la altura correcta'),
    ]),
    ('smartphones-tablets', 'Smartphones y tablets', 290, [
        ('smartphones-android', 'Smartphones Android', 'Smartphone', 'un smartphone Android con buena cámara y batería para todo el día'),
        ('iphone', 'iPhone', 'iPhone', 'un iPhone con iOS, cámaras avanzadas y años de actualizaciones'),
        ('tablets-android', 'Tablets Android', 'Tablet', 'una tablet Android para entretenimiento, estudio y lectura'),
        ('ipad', 'iPad', 'iPad', 'un iPad con iPadOS para crear, estudiar y trabajar'),
        ('tablets-profesionales', 'Tablets profesionales', 'Tablet profesional', 'una tablet de gama alta con lápiz y teclado opcionales'),
        ('accesorios-moviles', 'Accesorios móviles', 'Accesorio móvil', 'un accesorio práctico para tu smartphone'),
        ('cargadores', 'Cargadores', 'Cargador', 'un cargador rápido y seguro para tus dispositivos'),
        ('power-banks', 'Power banks', 'Power bank', 'una batería externa para cargar tus dispositivos fuera de casa'),
        ('protectores', 'Protectores', 'Protector', 'una protección resistente para tu dispositivo'),
        ('cables-usb-c', 'Cables USB-C', 'Cable', 'un cable resistente para carga rápida y transferencia de datos'),
    ]),
    ('gaming', 'Gaming', 330, [
        ('consolas', 'Consolas', 'Consola', 'una consola de videojuegos para jugar en casa o en movimiento'),
        ('controles', 'Controles', 'Control', 'un control inalámbrico cómodo y preciso'),
        ('accesorios-gaming', 'Accesorios gaming', 'Accesorio gaming', 'un accesorio para mejorar tu experiencia de juego'),
        ('teclados-mecanicos', 'Teclados mecánicos', 'Teclado mecánico', 'un teclado mecánico con switches de respuesta rápida'),
        ('mouse-gaming', 'Mouse gaming', 'Mouse gaming', 'un mouse gaming con sensor de alta precisión'),
        ('headsets-gaming', 'Headsets gaming', 'Headset gaming', 'un headset gaming con sonido envolvente y micrófono claro'),
        ('sillas-gaming', 'Sillas gaming', 'Silla gaming', 'una silla gaming ajustable para largas sesiones'),
        ('volantes', 'Volantes', 'Volante', 'un volante con pedales para simuladores de conducción'),
        ('mandos', 'Mandos arcade y especiales', 'Mando', 'un mando especial para juegos de lucha, vuelo o accesibilidad'),
        ('capturadoras', 'Capturadoras', 'Capturadora', 'una capturadora para grabar y transmitir partidas'),
        ('streaming-gaming', 'Accesorios para streaming', 'Accesorio de streaming', 'un accesorio para mejorar tus transmisiones en directo'),
    ]),
    ('perifericos', 'Periféricos', 45, [
        ('teclados', 'Teclados', 'Teclado', 'un teclado cómodo para escribir todo el día'),
        ('mouse', 'Mouse', 'Mouse', 'un mouse ergonómico y preciso'),
        ('webcams', 'Webcams', 'Webcam', 'una webcam con imagen nítida para videollamadas'),
        ('microfonos', 'Micrófonos', 'Micrófono', 'un micrófono con voz clara para reuniones y grabaciones'),
        ('impresoras', 'Impresoras', 'Impresora', 'una impresora para casa u oficina'),
        ('escaneres', 'Escáneres', 'Escáner', 'un escáner rápido para digitalizar documentos y fotos'),
        ('tabletas-digitalizadoras', 'Tabletas digitalizadoras', 'Tableta digitalizadora', 'una tableta digitalizadora para dibujo e ilustración'),
        ('docking-stations', 'Docking stations', 'Docking station', 'una docking station para conectar todo con un solo cable'),
        ('hubs-usb', 'Hubs USB', 'Hub USB', 'un hub para ampliar los puertos de tu equipo'),
        ('adaptadores', 'Adaptadores', 'Adaptador', 'un adaptador para conectar pantallas y periféricos'),
    ]),
    ('audio-entretenimiento', 'Audio y entretenimiento', 300, [
        ('audifonos-bluetooth', 'Audífonos Bluetooth', 'Audífonos', 'unos audífonos Bluetooth cómodos con cancelación de ruido'),
        ('audifonos-inalambricos', 'Audífonos inalámbricos (TWS)', 'Audífonos inalámbricos', 'unos audífonos true wireless compactos con estuche de carga'),
        ('audifonos-estudio', 'Audífonos de estudio', 'Audífonos de estudio', 'unos audífonos de estudio con respuesta precisa para mezcla y monitoreo'),
        ('parlantes', 'Parlantes', 'Parlante', 'un parlante portátil con sonido potente'),
        ('barras-sonido', 'Barras de sonido', 'Barra de sonido', 'una barra de sonido para mejorar el audio de tu TV'),
        ('dac', 'DAC y amplificadores', 'DAC', 'un DAC con amplificador para audífonos de alta fidelidad'),
        ('interfaces-audio', 'Interfaces de audio', 'Interfaz de audio', 'una interfaz de audio para grabar voz e instrumentos'),
        ('microfonos-usb', 'Micrófonos USB', 'Micrófono USB', 'un micrófono USB listo para podcast y streaming'),
        ('sistemas-sonido', 'Sistemas de sonido', 'Sistema de sonido', 'un sistema de sonido para tu escritorio o sala'),
        ('reproductores-multimedia', 'Reproductores multimedia', 'Reproductor', 'un reproductor de audio digital de alta resolución'),
    ]),
    ('redes', 'Redes y conectividad', 180, [
        ('routers', 'Routers', 'Router', 'un router fiable para el hogar'),
        ('routers-wifi-6-7', 'Routers Wi-Fi 6/7', 'Router', 'un router de última generación con Wi-Fi 6 o Wi-Fi 7'),
        ('sistemas-mesh', 'Sistemas Mesh', 'Sistema Mesh', 'un sistema Mesh para cubrir toda la casa sin zonas muertas'),
        ('switches', 'Switches', 'Switch', 'un switch para ampliar tu red cableada'),
        ('access-points', 'Access points', 'Access point', 'un punto de acceso para redes Wi-Fi de oficina'),
        ('adaptadores-red', 'Adaptadores de red', 'Adaptador de red', 'un adaptador de red USB o Ethernet'),
        ('tarjetas-wifi', 'Tarjetas Wi-Fi', 'Tarjeta Wi-Fi', 'una tarjeta Wi-Fi PCIe con Bluetooth para PC de escritorio'),
        ('cables-ethernet', 'Cables Ethernet', 'Cable Ethernet', 'un cable de red de alta velocidad'),
        ('fibra-optica', 'Equipos de fibra óptica', 'Equipo de fibra', 'un equipo para conexiones de fibra óptica'),
        ('accesorios-conectividad', 'Accesorios de conectividad', 'Accesorio de red', 'un accesorio para organizar y ampliar tu red'),
    ]),
    ('almacenamiento', 'Almacenamiento', 220, [
        ('ssd-externos', 'SSD externos', 'SSD externo', 'un SSD externo rápido y resistente'),
        ('hdd-externos', 'HDD externos', 'Disco duro externo', 'un disco duro externo para copias de seguridad'),
        ('memorias-usb', 'Memorias USB', 'Memoria USB', 'una memoria USB compacta y veloz'),
        ('tarjetas-microsd', 'Tarjetas microSD', 'Tarjeta microSD', 'una tarjeta microSD para cámaras, drones y consolas'),
        ('nas', 'NAS', 'NAS', 'un servidor NAS para guardar y compartir archivos en casa'),
        ('unidades-respaldo', 'Unidades de respaldo', 'Unidad de respaldo', 'una unidad para copias de seguridad automáticas'),
        ('accesorios-almacenamiento', 'Accesorios de almacenamiento', 'Accesorio de almacenamiento', 'un accesorio para conectar y proteger tus unidades'),
    ]),
    ('smart-home', 'Smart Home', 140, [
        ('focos-inteligentes', 'Focos inteligentes', 'Foco inteligente', 'un foco inteligente regulable desde el celular'),
        ('enchufes-inteligentes', 'Enchufes inteligentes', 'Enchufe inteligente', 'un enchufe inteligente con programación y control remoto'),
        ('camaras-inteligentes', 'Cámaras inteligentes', 'Cámara inteligente', 'una cámara inteligente para vigilar tu casa desde el celular'),
        ('sensores', 'Sensores', 'Sensor', 'un sensor para automatizar tu hogar'),
        ('asistentes-inteligentes', 'Asistentes inteligentes', 'Asistente inteligente', 'un parlante o pantalla con asistente de voz'),
        ('cerraduras-inteligentes', 'Cerraduras inteligentes', 'Cerradura inteligente', 'una cerradura inteligente con huella, clave y app'),
        ('controladores-smart', 'Controladores y hubs', 'Controlador', 'un controlador para unir tus dispositivos inteligentes'),
        ('automatizacion', 'Automatización doméstica', 'Dispositivo de automatización', 'un dispositivo para automatizar tareas del hogar'),
    ]),
    ('seguridad', 'Seguridad electrónica', 0, [
        ('camaras-ip', 'Cámaras IP', 'Cámara IP', 'una cámara IP para exteriores con visión nocturna'),
        ('cctv', 'Sistemas CCTV', 'Kit CCTV', 'un sistema de videovigilancia completo listo para instalar'),
        ('nvr', 'Grabadores NVR', 'Grabador NVR', 'un grabador de red para cámaras IP'),
        ('videoporteros', 'Videoporteros', 'Videoportero', 'un videoportero para ver y hablar con tus visitas'),
        ('alarmas', 'Alarmas', 'Alarma', 'un sistema de alarma con sirena y aviso al celular'),
        ('sensores-movimiento', 'Sensores de movimiento', 'Sensor de movimiento', 'un sensor de movimiento para tu sistema de seguridad'),
        ('control-acceso', 'Control de acceso', 'Control de acceso', 'un equipo de control de acceso con tarjeta o biometría'),
    ]),
    ('televisores', 'Televisores y entretenimiento digital', 230, [
        ('smart-tv', 'Smart TV', 'Smart TV', 'un Smart TV con aplicaciones de streaming integradas'),
        ('oled', 'OLED', 'TV OLED', 'un televisor OLED con negros perfectos'),
        ('qled', 'QLED', 'TV QLED', 'un televisor QLED con colores intensos y alto brillo'),
        ('mini-led', 'Mini LED', 'TV Mini LED', 'un televisor Mini LED con atenuación local de alta precisión'),
        ('proyectores', 'Proyectores', 'Proyector', 'un proyector para cine en casa o presentaciones'),
        ('tv-box', 'TV Box', 'TV Box', 'un TV Box para convertir cualquier televisor en Smart TV'),
        ('streaming', 'Dispositivos de streaming', 'Dispositivo de streaming', 'un dispositivo compacto para ver tus plataformas favoritas'),
        ('accesorios-video', 'Accesorios de video', 'Accesorio de video', 'un accesorio para instalar y conectar tu TV'),
    ]),
    ('fotografia', 'Fotografía y creación de contenido', 30, [
        ('camaras-digitales', 'Cámaras digitales', 'Cámara', 'una cámara compacta para viajes y vlogs'),
        ('camaras-mirrorless', 'Cámaras mirrorless', 'Cámara mirrorless', 'una cámara mirrorless de lentes intercambiables'),
        ('lentes', 'Lentes', 'Lente', 'un lente para ampliar tus posibilidades creativas'),
        ('tripodes', 'Trípodes', 'Trípode', 'un trípode estable y ligero'),
        ('iluminacion-led', 'Iluminación LED', 'Iluminación LED', 'una luz LED regulable para foto y video'),
        ('microfonos-contenido', 'Micrófonos para creadores', 'Micrófono', 'un micrófono para grabar audio limpio en video'),
        ('estabilizadores', 'Estabilizadores', 'Estabilizador', 'un estabilizador de tres ejes para tomas fluidas'),
        ('capturadoras-video', 'Capturadoras de video', 'Capturadora', 'una capturadora para usar tu cámara como webcam o grabar señal HDMI'),
        ('equipos-streaming', 'Equipos de streaming', 'Equipo de streaming', 'un equipo para producir transmisiones en directo'),
        ('drones', 'Drones', 'Dron', 'un dron con cámara estabilizada para tomas aéreas'),
    ]),
    ('empresarial', 'Tecnología empresarial', 210, [
        ('servidores', 'Servidores', 'Servidor', 'un servidor para pequeñas y medianas empresas'),
        ('redes-empresariales', 'Equipos de red empresariales', 'Equipo de red', 'un equipo de red administrable para empresas'),
        ('ups-empresarial', 'UPS empresariales', 'UPS', 'un UPS para proteger servidores y equipos críticos'),
        ('nas-empresarial', 'Sistemas NAS empresariales', 'NAS empresarial', 'un NAS en rack o torre para equipos de trabajo'),
        ('estaciones-trabajo', 'Estaciones de trabajo', 'Estación de trabajo', 'una estación de trabajo de escritorio para cargas exigentes'),
        ('equipos-pos', 'Equipos POS', 'Terminal POS', 'un terminal punto de venta táctil'),
        ('lectores-codigos', 'Lectores de códigos', 'Lector de códigos', 'un lector de códigos de barras y QR'),
        ('impresoras-termicas', 'Impresoras térmicas', 'Impresora térmica', 'una impresora térmica para boletas y etiquetas'),
        ('centro-datos', 'Equipos para centros de datos', 'Equipo de centro de datos', 'un equipo para organizar y proteger tu centro de datos'),
    ]),
    ('robotica', 'Robótica y electrónica', 100, [
        ('arduino', 'Arduino', 'Placa', 'una placa Arduino para prototipos y aprendizaje'),
        ('raspberry-pi', 'Raspberry Pi', 'Computadora de placa', 'una Raspberry Pi para proyectos, servidores y robótica'),
        ('esp32', 'ESP32', 'Placa', 'una placa ESP32 con Wi-Fi y Bluetooth para IoT'),
        ('sensores-electronicos', 'Sensores electrónicos', 'Sensor', 'un sensor para proyectos de electrónica'),
        ('microcontroladores', 'Microcontroladores', 'Microcontrolador', 'una placa con microcontrolador para proyectos embebidos'),
        ('modulos-comunicacion', 'Módulos de comunicación', 'Módulo', 'un módulo de comunicación inalámbrica para tus proyectos'),
        ('kits-robotica', 'Kits de robótica', 'Kit de robótica', 'un kit de robótica para aprender construyendo'),
        ('motores', 'Motores', 'Motor', 'un motor para proyectos de robótica y automatización'),
        ('servomotores', 'Servomotores', 'Servomotor', 'un servomotor para movimientos precisos'),
        ('componentes-electronicos', 'Componentes electrónicos', 'Kit de componentes', 'un surtido de componentes para tu banco de trabajo'),
        ('instrumentos-medicion', 'Instrumentos de medición', 'Instrumento', 'un instrumento de medición para electrónica'),
    ]),
    ('impresion-3d', 'Impresión 3D y fabricación digital', 70, [
        ('impresoras-3d', 'Impresoras 3D', 'Impresora 3D', 'una impresora 3D rápida y fácil de calibrar'),
        ('filamentos', 'Filamentos', 'Filamento', 'un filamento de calidad constante para impresión FDM'),
        ('resinas', 'Resinas', 'Resina', 'una resina fotopolimérica para impresoras de resina'),
        ('repuestos-3d', 'Repuestos', 'Repuesto', 'un repuesto para mantener tu impresora 3D'),
        ('escaneres-3d', 'Escáneres 3D', 'Escáner 3D', 'un escáner 3D para digitalizar objetos'),
        ('fabricacion-digital', 'Accesorios de fabricación digital', 'Equipo de fabricación', 'un equipo para corte y grabado digital'),
    ]),
    ('wearables', 'Tecnología wearable', 160, [
        ('smartwatches', 'Smartwatches', 'Smartwatch', 'un smartwatch con notificaciones, salud y pagos'),
        ('smartbands', 'Smartbands', 'Smartband', 'una smartband ligera para actividad y sueño'),
        ('relojes-deportivos', 'Relojes deportivos inteligentes', 'Reloj deportivo', 'un reloj deportivo con GPS para entrenar'),
        ('anillos-inteligentes', 'Anillos inteligentes', 'Anillo inteligente', 'un anillo inteligente para medir sueño y actividad'),
        ('accesorios-wearable', 'Accesorios wearable', 'Accesorio wearable', 'un accesorio para tu reloj o pulsera inteligente'),
    ]),
    ('realidad-virtual', 'Realidad virtual y aumentada', 275, [
        ('visores-vr', 'Visores VR', 'Visor VR', 'un visor de realidad virtual para juegos y fitness'),
        ('accesorios-vr', 'Accesorios VR', 'Accesorio VR', 'un accesorio para mejorar la comodidad de tu visor'),
        ('controladores-vr', 'Controladores', 'Controlador VR', 'un controlador de movimiento para realidad virtual'),
        ('realidad-mixta', 'Realidad mixta y aumentada', 'Dispositivo de realidad mixta', 'un dispositivo de realidad mixta o aumentada'),
    ]),
    ('energia', 'Energía y protección eléctrica', 50, [
        ('ups', 'UPS', 'UPS', 'un UPS para proteger tu PC y router de cortes de luz'),
        ('estabilizadores-tension', 'Estabilizadores de tensión', 'Estabilizador', 'un estabilizador de voltaje para equipos electrónicos'),
        ('supresores', 'Supresores de sobretensión', 'Supresor', 'un supresor de picos con múltiples tomas'),
        ('cargadores-gan', 'Cargadores GaN', 'Cargador GaN', 'un cargador GaN compacto de alta potencia'),
        ('estaciones-energia', 'Estaciones de energía portátiles', 'Estación de energía', 'una estación de energía portátil para viajes y emergencias'),
        ('adaptadores-electricos', 'Adaptadores eléctricos', 'Adaptador', 'un adaptador eléctrico seguro para viajes'),
        ('regletas-inteligentes', 'Regletas inteligentes', 'Regleta inteligente', 'una regleta con tomas controlables desde el celular'),
    ]),
    ('productividad', 'Tecnología para productividad', 120, [
        ('videoconferencia', 'Equipos de videoconferencia', 'Equipo de videoconferencia', 'un equipo de videoconferencia para salas de reunión'),
        ('pizarras-digitales', 'Pizarras digitales', 'Pizarra digital', 'una pizarra digital interactiva para clases y reuniones'),
        ('presentadores', 'Presentadores inalámbricos', 'Presentador', 'un presentador inalámbrico con puntero'),
        ('colaboracion', 'Dispositivos de colaboración', 'Dispositivo de colaboración', 'un dispositivo para compartir pantalla y colaborar en reuniones'),
        ('accesorios-ergonomicos', 'Accesorios ergonómicos', 'Accesorio ergonómico', 'un accesorio ergonómico para tu estación de trabajo'),
    ]),
]

PRODUCTS = {
    # ---------------------------------------------------------------- Computadoras y laptops
    'laptops-oficina': [
        'Lenovo | IdeaPad Slim 3 15IRU9 | 2299 | 2599 | Procesador=Intel Core i5-1335U; RAM=16 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Gráficos=Intel Iris Xe; Batería=Hasta 10 h; Peso=1.62 kg; Sistema=Windows 11 Home',
        'HP | 15-fd0xxx Core i7 | 2899 |  | Procesador=Intel Core i7-1355U; RAM=16 GB DDR4; Almacenamiento=1 TB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Gráficos=Intel Iris Xe; Batería=Hasta 9 h; Peso=1.59 kg; Sistema=Windows 11 Home',
        'ASUS | Vivobook 15 X1504VA | 2149 |  | Procesador=Intel Core i5-1335U; RAM=8 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Gráficos=Intel Iris Xe; Batería=Hasta 8 h; Peso=1.70 kg; Sistema=Windows 11 Home',
        'Acer | Aspire 5 A515-58M | 2499 |  | Procesador=Intel Core i5-1335U; RAM=16 GB LPDDR5; Almacenamiento=512 GB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Gráficos=Intel Iris Xe; Batería=Hasta 9 h; Peso=1.76 kg; Sistema=Windows 11 Home',
        'Lenovo | IdeaPad Slim 5 14AHP9 | 3199 | 3499 | Procesador=AMD Ryzen 7 8845HS; RAM=16 GB LPDDR5x; Almacenamiento=1 TB SSD; Pantalla=14" OLED; Resolución=1920x1200; Gráficos=AMD Radeon 780M; Batería=Hasta 12 h; Peso=1.46 kg; Sistema=Windows 11 Home',
        'Dell | Inspiron 15 3530 | 2399 |  | Procesador=Intel Core i5-1334U; RAM=8 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=15.6" WVA; Resolución=1920x1080; Gráficos=Intel Iris Xe; Batería=Hasta 8 h; Peso=1.66 kg; Sistema=Windows 11 Home',
    ],
    'laptops-gaming': [
        'ASUS | TUF Gaming A15 FA507NV | 4299 | 4799 | Procesador=AMD Ryzen 7 7735HS; RAM=16 GB DDR5; Almacenamiento=512 GB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Frecuencia=144 Hz; GPU=NVIDIA GeForce RTX 4060 8 GB; Peso=2.20 kg; Sistema=Windows 11 Home',
        'Lenovo | LOQ 15IRX9 | 4599 |  | Procesador=Intel Core i7-13650HX; RAM=16 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Frecuencia=144 Hz; GPU=NVIDIA GeForce RTX 4060 8 GB; Peso=2.38 kg; Sistema=Windows 11 Home',
        'MSI | Katana 15 B13VFK | 4999 |  | Procesador=Intel Core i7-13620H; RAM=16 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Frecuencia=144 Hz; GPU=NVIDIA GeForce RTX 4060 8 GB; Peso=2.25 kg; Sistema=Windows 11 Home',
        'Acer | Nitro V 15 ANV15-51 | 3699 |  | Procesador=Intel Core i5-13420H; RAM=16 GB DDR5; Almacenamiento=512 GB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; Frecuencia=144 Hz; GPU=NVIDIA GeForce RTX 4050 6 GB; Peso=2.10 kg; Sistema=Windows 11 Home',
        'ASUS | ROG Strix G16 G614JV | 6799 | 7499 | Procesador=Intel Core i7-13650HX; RAM=16 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=16" IPS; Resolución=2560x1600; Frecuencia=240 Hz; GPU=NVIDIA GeForce RTX 4060 8 GB; Peso=2.50 kg; Sistema=Windows 11 Home',
        'Lenovo | Legion Pro 5 16IRX9 | 8499 |  | Procesador=Intel Core i9-14900HX; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=16" IPS; Resolución=2560x1600; Frecuencia=240 Hz; GPU=NVIDIA GeForce RTX 4070 8 GB; Peso=2.50 kg; Sistema=Windows 11 Home',
        'HP | Victus 16-r0xxx | 4399 |  | Procesador=Intel Core i5-13500H; RAM=16 GB DDR5; Almacenamiento=512 GB SSD; Pantalla=16.1" IPS; Resolución=1920x1080; Frecuencia=144 Hz; GPU=NVIDIA GeForce RTX 4050 6 GB; Peso=2.31 kg; Sistema=Windows 11 Home',
        'MSI | Raider GE78 HX 14V | 13999 |  | Procesador=Intel Core i9-14900HX; RAM=32 GB DDR5; Almacenamiento=2 TB SSD; Pantalla=17" IPS; Resolución=2560x1600; Frecuencia=240 Hz; GPU=NVIDIA GeForce RTX 4080 12 GB; Peso=3.10 kg; Sistema=Windows 11 Pro',
    ],
    'ultrabooks': [
        'Apple | MacBook Air 13 M3 | 5299 |  | Procesador=Apple M3 8 núcleos; RAM=8 GB unificada; Almacenamiento=256 GB SSD; Pantalla=13.6" Liquid Retina; Resolución=2560x1664; Gráficos=Apple GPU 8 núcleos; Batería=Hasta 18 h; Peso=1.24 kg; Sistema=macOS',
        'Apple | MacBook Air 15 M3 | 6799 |  | Procesador=Apple M3 8 núcleos; RAM=16 GB unificada; Almacenamiento=512 GB SSD; Pantalla=15.3" Liquid Retina; Resolución=2880x1864; Gráficos=Apple GPU 10 núcleos; Batería=Hasta 18 h; Peso=1.51 kg; Sistema=macOS',
        'ASUS | Zenbook 14 OLED UX3405MA | 4699 | 5199 | Procesador=Intel Core Ultra 7 155H; RAM=16 GB LPDDR5x; Almacenamiento=1 TB SSD; Pantalla=14" OLED; Resolución=2880x1800; Gráficos=Intel Arc; Batería=Hasta 15 h; Peso=1.20 kg; Sistema=Windows 11 Home',
        'Dell | XPS 13 9340 | 6999 |  | Procesador=Intel Core Ultra 7 155H; RAM=16 GB LPDDR5x; Almacenamiento=512 GB SSD; Pantalla=13.4" IPS; Resolución=1920x1200; Gráficos=Intel Arc; Batería=Hasta 14 h; Peso=1.19 kg; Sistema=Windows 11 Home',
        'Lenovo | Yoga Slim 7x 14Q8X9 | 5499 |  | Procesador=Qualcomm Snapdragon X Elite; RAM=16 GB LPDDR5x; Almacenamiento=512 GB SSD; Pantalla=14.5" OLED; Resolución=2944x1840; Gráficos=Qualcomm Adreno; Batería=Hasta 20 h; Peso=1.28 kg; Sistema=Windows 11 Home',
        'HP | Spectre x360 14-eu0xxx | 7299 |  | Procesador=Intel Core Ultra 7 155H; RAM=16 GB LPDDR5x; Almacenamiento=1 TB SSD; Pantalla=14" OLED táctil; Resolución=2880x1800; Gráficos=Intel Arc; Batería=Hasta 13 h; Peso=1.44 kg; Sistema=Windows 11 Home',
    ],
    'workstations': [
        'Lenovo | ThinkPad P16s Gen 2 | 7999 |  | Procesador=Intel Core i7-1360P; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=16" IPS; Resolución=1920x1200; GPU=NVIDIA RTX A500 4 GB; Certificación=ISV; Peso=1.82 kg; Sistema=Windows 11 Pro',
        'Dell | Precision 3581 | 8899 |  | Procesador=Intel Core i7-13700H; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=15.6" IPS; Resolución=1920x1080; GPU=NVIDIA RTX A1000 6 GB; Certificación=ISV; Peso=1.79 kg; Sistema=Windows 11 Pro',
        'HP | ZBook Power G10 | 9499 |  | Procesador=Intel Core i9-13900H; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=15.6" IPS; Resolución=2560x1440; GPU=NVIDIA RTX 2000 Ada 8 GB; Certificación=ISV; Peso=1.90 kg; Sistema=Windows 11 Pro',
    ],
    'computadoras-escritorio': [
        'HP | Victus 15L TG02 | 4299 |  | Procesador=Intel Core i5-13400F; RAM=16 GB DDR4; Almacenamiento=1 TB SSD; GPU=NVIDIA GeForce RTX 4060 8 GB; Conectividad=Wi-Fi 6, Bluetooth 5.3; Formato=Torre; Sistema=Windows 11 Home',
        'Lenovo | IdeaCentre 3 07IRB8 | 2399 |  | Procesador=Intel Core i5-13400; RAM=8 GB DDR4; Almacenamiento=512 GB SSD; Gráficos=Intel UHD 730; Conectividad=Wi-Fi 6, Bluetooth 5.2; Formato=Torre; Sistema=Windows 11 Home',
        'HexaOrders | Forge R7 4070 | 6299 | 6799 | Procesador=AMD Ryzen 7 7700X; RAM=32 GB DDR5; Almacenamiento=2 TB SSD; GPU=NVIDIA GeForce RTX 4070 12 GB; Conectividad=Wi-Fi 6E, Bluetooth 5.3; Formato=Torre ATX; Sistema=Windows 11 Home',
        'Dell | OptiPlex 7010 Tower | 3299 |  | Procesador=Intel Core i5-13500; RAM=16 GB DDR4; Almacenamiento=512 GB SSD; Gráficos=Intel UHD 770; Conectividad=Ethernet Gigabit; Formato=Torre; Sistema=Windows 11 Pro',
    ],
    'mini-pc': [
        'Intel | NUC 13 Pro Kit NUC13ANHi5 | 2199 |  | Procesador=Intel Core i5-1340P; RAM=16 GB DDR4; Almacenamiento=512 GB SSD; Conectividad=Wi-Fi 6E, Thunderbolt 4; Formato=Mini PC; Sistema=Windows 11 Pro',
        'Beelink | SER7 7840HS | 2499 |  | Procesador=AMD Ryzen 7 7840HS; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Gráficos=AMD Radeon 780M; Conectividad=Wi-Fi 6E, USB4; Formato=Mini PC; Sistema=Windows 11 Pro',
        'Apple | Mac mini M2 | 2999 |  | Procesador=Apple M2 8 núcleos; RAM=8 GB unificada; Almacenamiento=256 GB SSD; Gráficos=Apple GPU 10 núcleos; Conectividad=Wi-Fi 6E, Thunderbolt 4; Formato=Mini PC; Sistema=macOS',
        'ASUS | ExpertCenter PN42 | 1199 |  | Procesador=Intel N100; RAM=8 GB DDR4; Almacenamiento=256 GB SSD; Conectividad=Wi-Fi 6, Bluetooth 5.2; Formato=Mini PC; Sistema=Windows 11 Pro',
    ],
    'all-in-one': [
        'Apple | iMac 24 M3 | 6999 |  | Procesador=Apple M3 8 núcleos; RAM=8 GB unificada; Almacenamiento=256 GB SSD; Pantalla=24" Retina 4.5K; Resolución=4480x2520; Conectividad=Wi-Fi 6E, Bluetooth 5.3',
        'HP | All-in-One 27-cr0xxx | 3899 |  | Procesador=Intel Core i7-1355U; RAM=16 GB DDR4; Almacenamiento=1 TB SSD; Pantalla=27" IPS; Resolución=1920x1080; Conectividad=Wi-Fi 6, Bluetooth 5.3; Sistema=Windows 11 Home',
        'Lenovo | IdeaCentre AIO 3 24IAP7 | 2799 | 3099 | Procesador=Intel Core i5-12450H; RAM=8 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=23.8" IPS; Resolución=1920x1080; Conectividad=Wi-Fi 6, Bluetooth 5.1; Sistema=Windows 11 Home',
    ],
    'equipos-empresariales': [
        'Lenovo | ThinkPad E14 Gen 5 | 3799 |  | Procesador=Intel Core i7-1355U; RAM=16 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=14" IPS; Resolución=1920x1200; Seguridad=TPM 2.0, lector de huella; Garantía=3 años; Sistema=Windows 11 Pro',
        'Dell | Latitude 5440 | 4499 |  | Procesador=Intel Core i5-1345U vPro; RAM=16 GB DDR4; Almacenamiento=512 GB SSD; Pantalla=14" IPS; Resolución=1920x1080; Seguridad=TPM 2.0, cámara con obturador; Garantía=3 años; Sistema=Windows 11 Pro',
        'HP | EliteBook 840 G10 | 5699 |  | Procesador=Intel Core i7-1365U vPro; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; Pantalla=14" IPS; Resolución=1920x1200; Seguridad=HP Wolf Security, TPM 2.0; Garantía=3 años; Sistema=Windows 11 Pro',
    ],
    'accesorios-laptop': [
        'Targus | CityLite Pro 15.6 | 229 |  | Compatibilidad=Laptops de hasta 15.6"; Material=Poliéster repelente al agua; Compartimentos=Funda acolchada y bolsillo para tablet; Capacidad=20 L',
        'Samsonite | Guardit Classy 15.6 | 349 |  | Compatibilidad=Laptops de hasta 15.6"; Material=Poliéster reciclado; Compartimentos=Bolsillo antirrobo y puerto USB; Capacidad=22 L',
        'Rain Design | mStand | 219 |  | Compatibilidad=Laptops de 11" a 16"; Material=Aluminio anodizado; Altura=15 cm; Peso=1.1 kg',
    ],
    # ---------------------------------------------------------------- Componentes de PC
    'procesadores-intel': [
        'Intel | Core i5-14400F | 899 |  | Núcleos=10 (6P + 4E); Hilos=16; Frecuencia máxima=4.7 GHz; Socket=LGA1700; TDP=65 W; Gráficos=No incluye',
        'Intel | Core i5-14600K | 1399 | 1549 | Núcleos=14 (6P + 8E); Hilos=20; Frecuencia máxima=5.3 GHz; Socket=LGA1700; TDP=125 W; Gráficos=Intel UHD 770',
        'Intel | Core i7-14700K | 1899 |  | Núcleos=20 (8P + 12E); Hilos=28; Frecuencia máxima=5.6 GHz; Socket=LGA1700; TDP=125 W; Gráficos=Intel UHD 770',
        'Intel | Core i9-14900K | 2599 |  | Núcleos=24 (8P + 16E); Hilos=32; Frecuencia máxima=6.0 GHz; Socket=LGA1700; TDP=125 W; Gráficos=Intel UHD 770',
        'Intel | Core i3-14100 | 599 |  | Núcleos=4; Hilos=8; Frecuencia máxima=4.7 GHz; Socket=LGA1700; TDP=60 W; Gráficos=Intel UHD 730',
    ],
    'procesadores-amd': [
        'AMD | Ryzen 5 7600 | 899 |  | Núcleos=6; Hilos=12; Frecuencia máxima=5.1 GHz; Socket=AM5; TDP=65 W; Gráficos=AMD Radeon integrada',
        'AMD | Ryzen 7 7800X3D | 1799 | 1999 | Núcleos=8; Hilos=16; Frecuencia máxima=5.0 GHz; Socket=AM5; TDP=120 W; Caché=96 MB L3 3D V-Cache',
        'AMD | Ryzen 9 7950X | 2499 |  | Núcleos=16; Hilos=32; Frecuencia máxima=5.7 GHz; Socket=AM5; TDP=170 W; Gráficos=AMD Radeon integrada',
        'AMD | Ryzen 5 5600 | 499 |  | Núcleos=6; Hilos=12; Frecuencia máxima=4.4 GHz; Socket=AM4; TDP=65 W; Gráficos=No incluye',
        'AMD | Ryzen 7 9700X | 1599 |  | Núcleos=8; Hilos=16; Frecuencia máxima=5.5 GHz; Socket=AM5; TDP=65 W; Gráficos=AMD Radeon integrada',
    ],
    'tarjetas-graficas-nvidia': [
        'ASUS | Dual GeForce RTX 4060 OC 8GB | 1349 |  | GPU=NVIDIA GeForce RTX 4060; Memoria=8 GB GDDR6; Interfaz=PCIe 4.0 x8; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=550 W',
        'MSI | GeForce RTX 4060 Ti Ventus 2X 8G | 1799 |  | GPU=NVIDIA GeForce RTX 4060 Ti; Memoria=8 GB GDDR6; Interfaz=PCIe 4.0 x8; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=550 W',
        'Gigabyte | GeForce RTX 4070 Super Windforce OC 12G | 2899 | 3199 | GPU=NVIDIA GeForce RTX 4070 Super; Memoria=12 GB GDDR6X; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=750 W',
        'Zotac | Gaming GeForce RTX 4070 Ti Super Trinity 16GB | 3799 |  | GPU=NVIDIA GeForce RTX 4070 Ti Super; Memoria=16 GB GDDR6X; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=750 W',
        'ASUS | TUF Gaming GeForce RTX 4080 Super 16GB | 4999 |  | GPU=NVIDIA GeForce RTX 4080 Super; Memoria=16 GB GDDR6X; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 2x HDMI; Fuente recomendada=850 W',
        'MSI | GeForce RTX 3050 Ventus 2X 6G | 899 |  | GPU=NVIDIA GeForce RTX 3050; Memoria=6 GB GDDR6; Interfaz=PCIe 4.0 x8; Salidas=2x DisplayPort, 1x HDMI; Fuente recomendada=300 W',
    ],
    'tarjetas-graficas-amd': [
        'Sapphire | Pulse Radeon RX 7600 8GB | 1149 |  | GPU=AMD Radeon RX 7600; Memoria=8 GB GDDR6; Interfaz=PCIe 4.0 x8; Salidas=2x DisplayPort, 2x HDMI; Fuente recomendada=550 W',
        'XFX | Speedster QICK 319 Radeon RX 7800 XT 16GB | 2299 | 2499 | GPU=AMD Radeon RX 7800 XT; Memoria=16 GB GDDR6; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=700 W',
        'PowerColor | Hellhound Radeon RX 7900 XT 20GB | 3399 |  | GPU=AMD Radeon RX 7900 XT; Memoria=20 GB GDDR6; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=750 W',
        'ASRock | Challenger Radeon RX 7700 XT 12GB | 1899 |  | GPU=AMD Radeon RX 7700 XT; Memoria=12 GB GDDR6; Interfaz=PCIe 4.0 x16; Salidas=3x DisplayPort, 1x HDMI; Fuente recomendada=700 W',
    ],
    'placas-madre': [
        'ASUS | TUF Gaming B760M-Plus WiFi D4 | 799 |  | Socket=LGA1700; Chipset=Intel B760; Memoria=4x DDR4 hasta 128 GB; Formato=Micro-ATX; Conectividad=Wi-Fi 6, Bluetooth 5.2; Almacenamiento=2x M.2 NVMe',
        'MSI | MAG B650 Tomahawk WiFi | 1099 |  | Socket=AM5; Chipset=AMD B650; Memoria=4x DDR5 hasta 192 GB; Formato=ATX; Conectividad=Wi-Fi 6E, Bluetooth 5.3; Almacenamiento=3x M.2 NVMe',
        'Gigabyte | Z790 Aorus Elite AX | 1299 |  | Socket=LGA1700; Chipset=Intel Z790; Memoria=4x DDR5 hasta 192 GB; Formato=ATX; Conectividad=Wi-Fi 6E, Bluetooth 5.3; Almacenamiento=4x M.2 NVMe',
        'ASRock | B550M Pro4 | 449 |  | Socket=AM4; Chipset=AMD B550; Memoria=4x DDR4 hasta 128 GB; Formato=Micro-ATX; Conectividad=Ethernet Gigabit; Almacenamiento=2x M.2 NVMe',
    ],
    'memorias-ram': [
        'Kingston | Fury Beast 16GB (2x8GB) DDR4-3200 | 189 |  | RAM=16 GB; Tipo=DDR4; Velocidad=3200 MT/s; Latencia=CL16; Kit=2 x 8 GB; Formato=DIMM',
        'Corsair | Vengeance 32GB (2x16GB) DDR5-6000 | 429 | 489 | RAM=32 GB; Tipo=DDR5; Velocidad=6000 MT/s; Latencia=CL36; Kit=2 x 16 GB; Formato=DIMM',
        'G.Skill | Trident Z5 RGB 32GB (2x16GB) DDR5-6400 | 549 |  | RAM=32 GB; Tipo=DDR5; Velocidad=6400 MT/s; Latencia=CL32; Kit=2 x 16 GB; Formato=DIMM',
        'Crucial | 16GB DDR5-5600 SODIMM | 199 |  | RAM=16 GB; Tipo=DDR5; Velocidad=5600 MT/s; Latencia=CL46; Kit=1 x 16 GB; Formato=SODIMM (laptop)',
        'TeamGroup | T-Force Delta RGB 64GB (2x32GB) DDR5-6000 | 899 |  | RAM=64 GB; Tipo=DDR5; Velocidad=6000 MT/s; Latencia=CL38; Kit=2 x 32 GB; Formato=DIMM',
    ],
    'ssd-nvme': [
        'Samsung | 990 Pro 1TB | 499 | 569 | Capacidad=1 TB; Interfaz=PCIe 4.0 x4 NVMe; Lectura=7450 MB/s; Escritura=6900 MB/s; Formato=M.2 2280; Garantía=5 años',
        'WD | Black SN850X 2TB | 799 |  | Capacidad=2 TB; Interfaz=PCIe 4.0 x4 NVMe; Lectura=7300 MB/s; Escritura=6600 MB/s; Formato=M.2 2280; Garantía=5 años',
        'Kingston | NV2 1TB | 249 |  | Capacidad=1 TB; Interfaz=PCIe 4.0 x4 NVMe; Lectura=3500 MB/s; Escritura=2100 MB/s; Formato=M.2 2280; Garantía=3 años',
        'Crucial | T500 2TB | 699 |  | Capacidad=2 TB; Interfaz=PCIe 4.0 x4 NVMe; Lectura=7400 MB/s; Escritura=7000 MB/s; Formato=M.2 2280; Garantía=5 años',
        'Lexar | NM790 4TB | 1249 |  | Capacidad=4 TB; Interfaz=PCIe 4.0 x4 NVMe; Lectura=7400 MB/s; Escritura=6500 MB/s; Formato=M.2 2280; Garantía=5 años',
    ],
    'ssd-sata': [
        'Samsung | 870 EVO 500GB | 249 |  | Capacidad=500 GB; Interfaz=SATA III; Lectura=560 MB/s; Escritura=530 MB/s; Formato=2.5"; Garantía=5 años',
        'Crucial | MX500 1TB | 299 |  | Capacidad=1 TB; Interfaz=SATA III; Lectura=560 MB/s; Escritura=510 MB/s; Formato=2.5"; Garantía=5 años',
        'Kingston | A400 480GB | 129 |  | Capacidad=480 GB; Interfaz=SATA III; Lectura=500 MB/s; Escritura=450 MB/s; Formato=2.5"; Garantía=3 años',
    ],
    'discos-duros': [
        'Seagate | BarraCuda 2TB ST2000DM008 | 259 |  | Capacidad=2 TB; Interfaz=SATA III; Velocidad=7200 rpm; Caché=256 MB; Formato=3.5"; Uso=Escritorio',
        'WD | Red Plus 4TB WD40EFPX | 549 |  | Capacidad=4 TB; Interfaz=SATA III; Velocidad=5400 rpm; Caché=256 MB; Formato=3.5"; Uso=NAS 24/7',
        'Toshiba | X300 8TB | 899 |  | Capacidad=8 TB; Interfaz=SATA III; Velocidad=7200 rpm; Caché=256 MB; Formato=3.5"; Uso=Alto rendimiento',
    ],
    'fuentes-poder': [
        'Corsair | RM750e 750W 80+ Gold | 449 |  | Potencia=750 W; Certificación=80 Plus Gold; Modularidad=Totalmente modular; Ventilador=120 mm; Estándar=ATX 3.0, PCIe 5.0; Garantía=7 años',
        'EVGA | 600 W1 80+ White | 189 |  | Potencia=600 W; Certificación=80 Plus White; Modularidad=No modular; Ventilador=120 mm; Estándar=ATX 2.3; Garantía=3 años',
        'Seasonic | Focus GX-850 80+ Gold | 599 | 649 | Potencia=850 W; Certificación=80 Plus Gold; Modularidad=Totalmente modular; Ventilador=135 mm; Estándar=ATX 3.0; Garantía=10 años',
        'be quiet! | Dark Power 13 1000W 80+ Titanium | 1199 |  | Potencia=1000 W; Certificación=80 Plus Titanium; Modularidad=Totalmente modular; Ventilador=135 mm silencioso; Estándar=ATX 3.0, PCIe 5.0; Garantía=10 años',
    ],
    'refrigeracion-aire': [
        'Cooler Master | Hyper 212 Black Edition | 149 |  | Tipo=Torre de aire; Ventilador=1x 120 mm; Heatpipes=4; Compatibilidad=LGA1700, AM5, AM4; Ruido=26 dBA; Altura=159 mm',
        'Noctua | NH-D15 chromax.black | 499 |  | Tipo=Doble torre de aire; Ventilador=2x 140 mm; Heatpipes=6; Compatibilidad=LGA1700, AM5, AM4; Ruido=24.6 dBA; Altura=165 mm',
        'DeepCool | AK620 Digital | 299 |  | Tipo=Doble torre con pantalla; Ventilador=2x 120 mm; Heatpipes=6; Compatibilidad=LGA1700, AM5, AM4; Ruido=28 dBA; Altura=162 mm',
    ],
    'refrigeracion-liquida': [
        'NZXT | Kraken 240 RGB | 649 |  | Radiador=240 mm; Ventiladores=2x 120 mm RGB; Bomba=Con pantalla LCD; Compatibilidad=LGA1700, AM5, AM4; Garantía=6 años',
        'Corsair | iCUE H150i Elite LCD XT | 999 |  | Radiador=360 mm; Ventiladores=3x 120 mm RGB; Bomba=Con pantalla LCD; Compatibilidad=LGA1700, AM5, AM4; Garantía=5 años',
        'Arctic | Liquid Freezer III 280 | 429 |  | Radiador=280 mm; Ventiladores=2x 140 mm; Bomba=Con ventilador VRM; Compatibilidad=LGA1700, AM5, AM4; Garantía=6 años',
    ],
    'gabinetes': [
        'Lian Li | O11 Dynamic EVO | 699 |  | Formato=ATX Mid Tower; Placas=E-ATX, ATX, mATX, ITX; Paneles=Vidrio templado; Ventiladores incluidos=No; GPU máxima=422 mm',
        'NZXT | H5 Flow | 399 |  | Formato=ATX Mid Tower; Placas=ATX, mATX, ITX; Paneles=Vidrio templado y malla; Ventiladores incluidos=2x 120 mm; GPU máxima=365 mm',
        'Corsair | 4000D Airflow | 449 |  | Formato=ATX Mid Tower; Placas=ATX, mATX, ITX; Paneles=Vidrio templado y malla; Ventiladores incluidos=2x 120 mm; GPU máxima=360 mm',
        'Cooler Master | MasterBox Q300L | 199 |  | Formato=Micro-ATX; Placas=mATX, ITX; Paneles=Acrílico; Ventiladores incluidos=1x 120 mm; GPU máxima=360 mm',
    ],
    'ventiladores': [
        'Arctic | P12 PWM PST 5-Pack | 129 |  | Tamaño=120 mm; Unidades=5; Velocidad=200 a 1800 rpm; Conector=4 pines PWM; Iluminación=Sin RGB',
        'Corsair | iCUE AF120 RGB Elite 3-Pack | 349 |  | Tamaño=120 mm; Unidades=3; Velocidad=400 a 2100 rpm; Conector=4 pines PWM; Iluminación=RGB direccionable',
        'Noctua | NF-A14 PWM | 129 |  | Tamaño=140 mm; Unidades=1; Velocidad=300 a 1500 rpm; Conector=4 pines PWM; Iluminación=Sin RGB',
    ],
    'accesorios-ensamblaje': [
        'Arctic | MX-6 Pasta térmica 4 g | 39 |  | Conductividad=Alta; Contenido=4 g; Aplicación=CPU y GPU; Curado=No requiere',
        'iFixit | Mako Precision Bit Set 64 piezas | 179 |  | Puntas=64; Material=Acero S2; Mango=Aluminio con imán; Uso=Laptops, PC y consolas',
        'Cable Matters | Kit de bridas y organizadores 100 piezas | 49 |  | Contenido=100 piezas; Material=Nylon reutilizable; Uso=Gestión de cables del gabinete',
    ],
}

PRODUCTS.update({
    # ---------------------------------------------------------------- Monitores y pantallas
    'monitores-full-hd': [
        'LG | 24MR400-B | 449 |  | Pantalla=23.8" IPS; Resolución=1920x1080; Frecuencia=100 Hz; Respuesta=5 ms; Conexiones=HDMI, VGA; Tecnología=AMD FreeSync',
        'Samsung | LS24C310EALXPE | 429 |  | Pantalla=24" IPS; Resolución=1920x1080; Frecuencia=75 Hz; Respuesta=5 ms; Conexiones=HDMI, VGA; Tecnología=Eye Saver',
        'AOC | 27B2HM2 | 549 | 599 | Pantalla=27" VA; Resolución=1920x1080; Frecuencia=100 Hz; Respuesta=4 ms; Conexiones=HDMI, VGA; Tecnología=Flicker Free',
    ],
    'monitores-qhd': [
        'Dell | S2722DC | 1299 |  | Pantalla=27" IPS; Resolución=2560x1440; Frecuencia=75 Hz; Respuesta=4 ms; Conexiones=USB-C 65 W, 2x HDMI; Tecnología=AMD FreeSync',
        'LG | 27QN600-B | 999 |  | Pantalla=27" IPS; Resolución=2560x1440; Frecuencia=75 Hz; Respuesta=5 ms; Conexiones=HDMI, DisplayPort; Tecnología=HDR10, sRGB 99%',
        'ASUS | ProArt PA278QV | 1399 |  | Pantalla=27" IPS; Resolución=2560x1440; Frecuencia=75 Hz; Respuesta=5 ms; Conexiones=HDMI, DisplayPort, USB-C; Tecnología=Calman Verified',
    ],
    'monitores-4k': [
        'LG | 27UP850N-W | 1799 |  | Pantalla=27" IPS; Resolución=3840x2160; Frecuencia=60 Hz; Respuesta=5 ms; Conexiones=USB-C 96 W, HDMI, DisplayPort; Tecnología=DisplayHDR 400',
        'Samsung | ViewFinity S80UD 27 | 1699 | 1899 | Pantalla=27" IPS; Resolución=3840x2160; Frecuencia=60 Hz; Respuesta=5 ms; Conexiones=USB-C 90 W, HDMI, DisplayPort; Tecnología=HDR10',
        'Dell | S3225QS | 1999 |  | Pantalla=31.5" VA curvo; Resolución=3840x2160; Frecuencia=120 Hz; Respuesta=4 ms; Conexiones=2x HDMI, DisplayPort; Tecnología=AMD FreeSync Premium',
    ],
    'monitores-gaming': [
        'ASUS | TUF Gaming VG27AQ3A | 1199 |  | Pantalla=27" Fast IPS; Resolución=2560x1440; Frecuencia=180 Hz; Respuesta=1 ms; Conexiones=2x HDMI, DisplayPort; Tecnología=G-Sync compatible',
        'Samsung | Odyssey G5 LS27CG552 | 1099 |  | Pantalla=27" VA curvo; Resolución=2560x1440; Frecuencia=165 Hz; Respuesta=1 ms; Conexiones=HDMI, DisplayPort; Tecnología=AMD FreeSync Premium',
        'LG | UltraGear 27GR95QE-B | 3299 | 3699 | Pantalla=27" OLED; Resolución=2560x1440; Frecuencia=240 Hz; Respuesta=0.03 ms; Conexiones=2x HDMI 2.1, DisplayPort; Tecnología=G-Sync compatible',
        'AOC | 24G2SP | 699 |  | Pantalla=23.8" IPS; Resolución=1920x1080; Frecuencia=165 Hz; Respuesta=1 ms; Conexiones=2x HDMI, DisplayPort; Tecnología=AMD FreeSync Premium',
        'MSI | MAG 271QPX QD-OLED E2 | 3499 |  | Pantalla=27" QD-OLED; Resolución=2560x1440; Frecuencia=240 Hz; Respuesta=0.03 ms; Conexiones=2x HDMI, DisplayPort, USB-C; Tecnología=DisplayHDR True Black 400',
    ],
    'monitores-ultrawide': [
        'LG | 34WR50QC-B | 1499 |  | Pantalla=34" VA curvo; Resolución=3440x1440; Frecuencia=100 Hz; Respuesta=5 ms; Conexiones=2x HDMI, DisplayPort; Tecnología=HDR10',
        'Samsung | Odyssey G9 OLED 49 | 6499 |  | Pantalla=49" QD-OLED curvo; Resolución=5120x1440; Frecuencia=240 Hz; Respuesta=0.03 ms; Conexiones=HDMI 2.1, DisplayPort; Tecnología=DisplayHDR True Black 400',
        'Dell | P3424WE | 2599 |  | Pantalla=34" IPS curvo; Resolución=3440x1440; Frecuencia=60 Hz; Respuesta=5 ms; Conexiones=USB-C 90 W, HDMI, DisplayPort, RJ45; Tecnología=ComfortView Plus',
    ],
    'monitores-profesionales': [
        'BenQ | SW272U | 4999 |  | Pantalla=27" IPS; Resolución=3840x2160; Frecuencia=60 Hz; Respuesta=5 ms; Conexiones=USB-C 90 W, HDMI, DisplayPort; Tecnología=Adobe RGB 99%, calibración por hardware',
        'ASUS | ProArt PA329CV | 3399 |  | Pantalla=32" IPS; Resolución=3840x2160; Frecuencia=60 Hz; Respuesta=5 ms; Conexiones=USB-C 90 W, 2x HDMI, DisplayPort; Tecnología=Calman Verified, HDR400',
        'Dell | UltraSharp U2724D | 1899 |  | Pantalla=27" IPS Black; Resolución=2560x1440; Frecuencia=120 Hz; Respuesta=5 ms; Conexiones=HDMI, 2x DisplayPort; Tecnología=sRGB 100%',
    ],
    'monitores-portatiles': [
        'ASUS | ZenScreen MB16ACV | 799 |  | Pantalla=15.6" IPS; Resolución=1920x1080; Frecuencia=60 Hz; Conexiones=USB-C; Peso=0.83 kg; Tecnología=Antibacteriano',
        'Lenovo | ThinkVision M14d | 1099 |  | Pantalla=14" IPS; Resolución=2240x1400; Frecuencia=60 Hz; Conexiones=2x USB-C; Peso=0.60 kg; Tecnología=Low Blue Light',
    ],
    'soportes-monitor': [
        'Ergotron | LX Brazo para monitor | 899 |  | Compatibilidad=Monitores hasta 34"; Carga=3.2 a 11.3 kg; VESA=75x75, 100x100; Ajustes=Altura, inclinación y giro',
        'North Bayou | F80 Brazo con resorte de gas | 199 |  | Compatibilidad=Monitores de 17" a 30"; Carga=2 a 9 kg; VESA=75x75, 100x100; Ajustes=Altura, inclinación y giro',
        'HexaOrders | Soporte doble para monitores DM-2 | 279 |  | Compatibilidad=Dos monitores de hasta 32"; Carga=Hasta 8 kg por brazo; VESA=75x75, 100x100; Ajustes=Altura, inclinación y giro',
    ],
    # ---------------------------------------------------------------- Smartphones y tablets
    'smartphones-android': [
        'Samsung | Galaxy A55 5G 256GB | 1699 | 1899 | Procesador=Samsung Exynos 1480; RAM=8 GB; Almacenamiento=256 GB; Pantalla=6.6" Super AMOLED; Frecuencia=120 Hz; Cámara=50 MP + 12 MP + 5 MP; Batería=5000 mAh; Conectividad=5G, Wi-Fi 6, Bluetooth 5.3',
        'Samsung | Galaxy S24 256GB | 3699 |  | Procesador=Samsung Exynos 2400; RAM=8 GB; Almacenamiento=256 GB; Pantalla=6.2" Dynamic AMOLED 2X; Frecuencia=120 Hz; Cámara=50 MP + 12 MP + 10 MP; Batería=4000 mAh; Conectividad=5G, Wi-Fi 6E, Bluetooth 5.3',
        'Samsung | Galaxy S24 Ultra 512GB | 5999 |  | Procesador=Qualcomm Snapdragon 8 Gen 3; RAM=12 GB; Almacenamiento=512 GB; Pantalla=6.8" Dynamic AMOLED 2X; Frecuencia=120 Hz; Cámara=200 MP + 50 MP + 12 MP + 10 MP; Batería=5000 mAh; Conectividad=5G, Wi-Fi 7, Bluetooth 5.3',
        'Xiaomi | Redmi Note 13 Pro 256GB | 1299 |  | Procesador=MediaTek Helio G99 Ultra; RAM=8 GB; Almacenamiento=256 GB; Pantalla=6.67" AMOLED; Frecuencia=120 Hz; Cámara=200 MP + 8 MP + 2 MP; Batería=5000 mAh; Conectividad=4G, Wi-Fi 5, Bluetooth 5.2',
        'Motorola | Moto G85 5G 256GB | 1099 |  | Procesador=Qualcomm Snapdragon 6s Gen 3; RAM=8 GB; Almacenamiento=256 GB; Pantalla=6.67" pOLED; Frecuencia=120 Hz; Cámara=50 MP + 8 MP; Batería=5000 mAh; Conectividad=5G, Wi-Fi 5, Bluetooth 5.1',
        'Google | Pixel 8a 128GB | 2299 |  | Procesador=Google Tensor G3; RAM=8 GB; Almacenamiento=128 GB; Pantalla=6.1" OLED; Frecuencia=120 Hz; Cámara=64 MP + 13 MP; Batería=4492 mAh; Conectividad=5G, Wi-Fi 6E, Bluetooth 5.3',
        'Xiaomi | 14T Pro 512GB | 3499 |  | Procesador=MediaTek Dimensity 9300+; RAM=12 GB; Almacenamiento=512 GB; Pantalla=6.67" AMOLED; Frecuencia=144 Hz; Cámara=50 MP + 50 MP + 12 MP; Batería=5000 mAh; Conectividad=5G, Wi-Fi 7, Bluetooth 5.4',
        'Honor | Magic6 Lite 256GB | 1399 |  | Procesador=Qualcomm Snapdragon 6 Gen 1; RAM=8 GB; Almacenamiento=256 GB; Pantalla=6.78" AMOLED curva; Frecuencia=120 Hz; Cámara=108 MP + 5 MP + 2 MP; Batería=5300 mAh; Conectividad=5G, Wi-Fi 5, Bluetooth 5.1',
    ],
    'iphone': [
        'Apple | iPhone 15 128GB | 3999 |  | Procesador=Apple A16 Bionic; Almacenamiento=128 GB; Pantalla=6.1" Super Retina XDR; Frecuencia=60 Hz; Cámara=48 MP + 12 MP; Batería=Hasta 20 h de video; Conectividad=5G, Wi-Fi 6, USB-C',
        'Apple | iPhone 15 Pro 256GB | 5899 |  | Procesador=Apple A17 Pro; Almacenamiento=256 GB; Pantalla=6.1" Super Retina XDR ProMotion; Frecuencia=120 Hz; Cámara=48 MP + 12 MP + 12 MP; Batería=Hasta 23 h de video; Conectividad=5G, Wi-Fi 6E, USB-C 3',
        'Apple | iPhone 14 128GB | 3299 | 3599 | Procesador=Apple A15 Bionic; Almacenamiento=128 GB; Pantalla=6.1" Super Retina XDR; Frecuencia=60 Hz; Cámara=12 MP + 12 MP; Batería=Hasta 20 h de video; Conectividad=5G, Wi-Fi 6, Lightning',
        'Apple | iPhone 15 Pro Max 512GB | 7999 |  | Procesador=Apple A17 Pro; Almacenamiento=512 GB; Pantalla=6.7" Super Retina XDR ProMotion; Frecuencia=120 Hz; Cámara=48 MP + 12 MP + 12 MP 5x; Batería=Hasta 29 h de video; Conectividad=5G, Wi-Fi 6E, USB-C 3',
    ],
    'tablets-android': [
        'Samsung | Galaxy Tab A9+ 128GB | 999 |  | Procesador=Qualcomm Snapdragon 695; RAM=8 GB; Almacenamiento=128 GB; Pantalla=11" TFT; Frecuencia=90 Hz; Batería=7040 mAh; Conectividad=Wi-Fi 5, Bluetooth 5.1',
        'Lenovo | Tab M11 128GB | 899 |  | Procesador=MediaTek Helio G88; RAM=4 GB; Almacenamiento=128 GB; Pantalla=10.95" IPS; Frecuencia=90 Hz; Batería=7040 mAh; Conectividad=Wi-Fi 5, Bluetooth 5.1',
        'Xiaomi | Pad 6 256GB | 1599 | 1799 | Procesador=Qualcomm Snapdragon 870; RAM=8 GB; Almacenamiento=256 GB; Pantalla=11" IPS 2.8K; Frecuencia=144 Hz; Batería=8840 mAh; Conectividad=Wi-Fi 6, Bluetooth 5.2',
    ],
    'ipad': [
        'Apple | iPad 10.ª gen 64GB Wi-Fi | 1999 |  | Procesador=Apple A14 Bionic; Almacenamiento=64 GB; Pantalla=10.9" Liquid Retina; Batería=Hasta 10 h; Conectividad=Wi-Fi 6, USB-C',
        'Apple | iPad Air 11 M2 128GB | 3299 |  | Procesador=Apple M2; Almacenamiento=128 GB; Pantalla=11" Liquid Retina; Batería=Hasta 10 h; Conectividad=Wi-Fi 6E, USB-C',
        'Apple | iPad mini 6 64GB | 2599 |  | Procesador=Apple A15 Bionic; Almacenamiento=64 GB; Pantalla=8.3" Liquid Retina; Batería=Hasta 10 h; Conectividad=Wi-Fi 6, USB-C',
    ],
    'tablets-profesionales': [
        'Apple | iPad Pro 13 M4 256GB | 7499 |  | Procesador=Apple M4; Almacenamiento=256 GB; Pantalla=13" Ultra Retina XDR OLED; Frecuencia=120 Hz; Batería=Hasta 10 h; Conectividad=Wi-Fi 6E, Thunderbolt',
        'Samsung | Galaxy Tab S9 FE+ 256GB | 2399 |  | Procesador=Samsung Exynos 1380; RAM=12 GB; Almacenamiento=256 GB; Pantalla=12.4" TFT; Frecuencia=90 Hz; Batería=10090 mAh; Conectividad=Wi-Fi 6, Bluetooth 5.3',
        'Samsung | Galaxy Tab S9 Ultra 512GB | 5999 |  | Procesador=Qualcomm Snapdragon 8 Gen 2; RAM=12 GB; Almacenamiento=512 GB; Pantalla=14.6" Dynamic AMOLED 2X; Frecuencia=120 Hz; Batería=11200 mAh; Conectividad=Wi-Fi 6E, Bluetooth 5.3',
        'Microsoft | Surface Pro 11 Snapdragon X Plus | 5699 |  | Procesador=Qualcomm Snapdragon X Plus; RAM=16 GB; Almacenamiento=512 GB SSD; Pantalla=13" PixelSense; Frecuencia=120 Hz; Batería=Hasta 14 h; Conectividad=Wi-Fi 7, USB4',
    ],
    'accesorios-moviles': [
        'PopSockets | MagSafe Grip | 89 |  | Compatibilidad=iPhone con MagSafe; Material=Aluminio y silicona; Función=Agarre y soporte',
        'Baseus | Soporte magnético para auto con carga 15 W | 149 |  | Compatibilidad=Smartphones con MagSafe; Potencia=15 W; Montaje=Rejilla de ventilación; Conectividad=USB-C',
        'Ugreen | Selfie stick trípode Bluetooth | 99 |  | Extensión=Hasta 100 cm; Control=Remoto Bluetooth; Compatibilidad=Smartphones de 4.7" a 6.8"; Conectividad=Bluetooth',
    ],
    'cargadores': [
        'Apple | Adaptador de corriente USB-C 20 W | 99 |  | Potencia=20 W; Puertos=1x USB-C; Carga rápida=Power Delivery; Compatibilidad=iPhone y iPad',
        'Samsung | Cargador rápido 45 W USB-C | 149 |  | Potencia=45 W; Puertos=1x USB-C; Carga rápida=Super Fast Charging 2.0; Compatibilidad=Galaxy y USB-C PD',
        'Anker | 313 Wireless Charger Pad 10 W | 79 |  | Potencia=10 W; Tipo=Inalámbrico Qi; Puertos=Micro-USB de entrada; Compatibilidad=Dispositivos con Qi',
    ],
    'power-banks': [
        'Anker | PowerCore 10000 PD Redux | 149 |  | Capacidad=10000 mAh; Potencia=20 W; Puertos=1x USB-C, 1x USB-A; Peso=194 g',
        'Xiaomi | Power Bank 20000 mAh 22.5 W | 129 |  | Capacidad=20000 mAh; Potencia=22.5 W; Puertos=1x USB-C, 2x USB-A; Peso=410 g',
        'Baseus | Blade HD 20000 mAh 100 W | 349 | 399 | Capacidad=20000 mAh; Potencia=100 W; Puertos=2x USB-C, 2x USB-A; Peso=480 g',
    ],
    'protectores': [
        'Spigen | Ultra Hybrid iPhone 15 | 99 |  | Compatibilidad=iPhone 15; Material=TPU y policarbonato; Protección=Certificación militar; Acabado=Transparente',
        'Samsung | Funda Silicone Case Galaxy S24 | 129 |  | Compatibilidad=Galaxy S24; Material=Silicona; Protección=Bordes elevados; Acabado=Mate',
        'Belkin | Protector de pantalla UltraGlass 2 iPhone 15 | 149 |  | Compatibilidad=iPhone 15; Material=Vidrio templado; Protección=Resistencia 9H; Acabado=Antihuellas',
    ],
    'cables-usb-c': [
        'Anker | Cable USB-C a USB-C 240 W 1.8 m | 69 |  | Longitud=1.8 m; Potencia=240 W; Datos=USB 2.0 480 Mbps; Material=Nylon trenzado',
        'Ugreen | Cable USB-C a Lightning MFi 1 m | 59 |  | Longitud=1 m; Potencia=20 W; Datos=USB 2.0; Certificación=Apple MFi',
        'Belkin | Cable Thunderbolt 4 USB-C 2 m | 249 |  | Longitud=2 m; Potencia=100 W; Datos=40 Gbps; Video=Hasta 8K',
    ],
    # ---------------------------------------------------------------- Gaming
    'consolas': [
        'Sony | PlayStation 5 Slim Digital | 2299 |  | Almacenamiento=1 TB SSD; Resolución=Hasta 4K a 120 Hz; Conectividad=Wi-Fi 6, Bluetooth 5.1; Incluye=Control DualSense',
        'Sony | PlayStation 5 Slim con lector | 2599 | 2799 | Almacenamiento=1 TB SSD; Resolución=Hasta 4K a 120 Hz; Conectividad=Wi-Fi 6, Bluetooth 5.1; Incluye=Control DualSense y lector Blu-ray',
        'Microsoft | Xbox Series X 1TB | 2499 |  | Almacenamiento=1 TB SSD; Resolución=Hasta 4K a 120 Hz; Conectividad=Wi-Fi 5, Bluetooth; Incluye=Control inalámbrico Xbox',
        'Microsoft | Xbox Series S 512GB | 1399 |  | Almacenamiento=512 GB SSD; Resolución=Hasta 1440p a 120 Hz; Conectividad=Wi-Fi 5, Bluetooth; Incluye=Control inalámbrico Xbox',
        'Nintendo | Switch OLED | 1599 |  | Almacenamiento=64 GB; Pantalla=7" OLED; Conectividad=Wi-Fi 5, Bluetooth 4.1; Incluye=Joy-Con y base con LAN',
        'Valve | Steam Deck OLED 512GB | 2799 |  | Procesador=AMD APU Zen 2; RAM=16 GB LPDDR5; Almacenamiento=512 GB SSD; Pantalla=7.4" OLED; Frecuencia=90 Hz; Conectividad=Wi-Fi 6E, Bluetooth 5.3',
    ],
    'controles': [
        'Sony | DualSense Wireless Controller | 299 |  | Compatibilidad=PS5, PC; Conectividad=Bluetooth, USB-C; Batería=Hasta 12 h; Funciones=Gatillos adaptativos y háptica',
        'Microsoft | Xbox Wireless Controller | 269 |  | Compatibilidad=Xbox, PC, móvil; Conectividad=Bluetooth, Xbox Wireless; Batería=2x AA (hasta 40 h); Funciones=Botón Compartir',
        '8BitDo | Ultimate 2.4G | 199 |  | Compatibilidad=PC, Android, Switch; Conectividad=2.4 GHz, Bluetooth; Batería=Hasta 22 h; Funciones=Base de carga y perfiles',
        'Nintendo | Pro Controller | 299 |  | Compatibilidad=Nintendo Switch; Conectividad=Bluetooth, USB-C; Batería=Hasta 40 h; Funciones=HD Rumble y amiibo',
    ],
    'accesorios-gaming': [
        'Sony | Estación de carga DualSense | 129 |  | Compatibilidad=Controles DualSense; Capacidad=2 controles; Conectividad=Alimentación por adaptador',
        'Logitech G | G440 Mousepad duro | 129 |  | Tamaño=340 x 280 mm; Superficie=Polímero de baja fricción; Base=Goma antideslizante',
        'SteelSeries | QcK Heavy XXL | 199 |  | Tamaño=900 x 400 mm; Superficie=Tela micro tejida; Base=Goma de 6 mm',
    ],
    'teclados-mecanicos': [
        'Logitech G | Pro X TKL Lightspeed | 799 |  | Formato=TKL (sin teclado numérico); Switches=GX Brown táctiles; Conectividad=Inalámbrico 2.4 GHz, Bluetooth, USB-C; Iluminación=RGB Lightsync; Batería=Hasta 50 h',
        'Razer | BlackWidow V4 X | 549 |  | Formato=Completo; Switches=Razer Green clicky; Conectividad=USB; Iluminación=RGB Chroma',
        'Redragon | Kumara K552 RGB | 149 |  | Formato=TKL (sin teclado numérico); Switches=Outemu Blue; Conectividad=USB; Iluminación=RGB',
        'Keychron | K2 Pro Hot-swap | 449 |  | Formato=75%; Switches=K Pro Brown intercambiables; Conectividad=Bluetooth 5.1, USB-C; Iluminación=RGB; Batería=Hasta 100 h',
        'HyperX | Alloy Origins 65 | 349 |  | Formato=65%; Switches=HyperX Red lineales; Conectividad=USB-C; Iluminación=RGB',
    ],
    'mouse-gaming': [
        'Logitech G | G502 X Plus | 599 |  | Sensor=HERO 25K; DPI=Hasta 25600; Conectividad=Inalámbrico 2.4 GHz; Peso=106 g; Batería=Hasta 130 h',
        'Razer | DeathAdder V3 Pro | 699 |  | Sensor=Focus Pro 30K; DPI=Hasta 30000; Conectividad=Inalámbrico 2.4 GHz; Peso=63 g; Batería=Hasta 90 h',
        'Logitech G | G203 Lightsync | 99 |  | Sensor=Óptico 8000 DPI; DPI=Hasta 8000; Conectividad=USB; Peso=85 g',
        'SteelSeries | Aerox 3 Wireless | 349 | 399 | Sensor=TrueMove Air; DPI=Hasta 18000; Conectividad=Inalámbrico 2.4 GHz, Bluetooth; Peso=68 g; Batería=Hasta 200 h',
    ],
    'headsets-gaming': [
        'HyperX | Cloud III Wireless | 599 |  | Tipo=Over-ear; Drivers=53 mm; Conectividad=Inalámbrico 2.4 GHz; Micrófono=Desmontable con cancelación de ruido; Batería=Hasta 120 h',
        'Logitech G | Pro X 2 Lightspeed | 899 |  | Tipo=Over-ear; Drivers=50 mm de grafeno; Conectividad=Inalámbrico 2.4 GHz, Bluetooth; Micrófono=Desmontable; Batería=Hasta 50 h',
        'Razer | BlackShark V2 X | 229 |  | Tipo=Over-ear; Drivers=50 mm; Conectividad=Jack 3.5 mm; Micrófono=Cardioide; Sonido=7.1 virtual',
        'Corsair | HS80 Max Wireless | 699 |  | Tipo=Over-ear; Drivers=50 mm; Conectividad=Inalámbrico 2.4 GHz, Bluetooth; Micrófono=Omnidireccional; Batería=Hasta 65 h',
    ],
    'sillas-gaming': [
        'Secretlab | Titan Evo L | 2499 |  | Material=Cuero sintético NEO Hybrid; Soporte lumbar=Integrado ajustable; Reclinación=85° a 165°; Peso máximo=180 kg; Apoyabrazos=4D',
        'Cougar | Armor One | 899 |  | Material=Cuero PVC; Soporte lumbar=Cojín; Reclinación=90° a 180°; Peso máximo=120 kg; Apoyabrazos=2D',
        'DXRacer | Formula Series | 1299 |  | Material=Cuero PU; Soporte lumbar=Cojín; Reclinación=90° a 135°; Peso máximo=110 kg; Apoyabrazos=3D',
    ],
    'volantes': [
        'Logitech G | G29 Driving Force | 1199 |  | Compatibilidad=PS5, PS4, PC; Rotación=900°; Force Feedback=Doble motor; Pedales=3 pedales incluidos',
        'Thrustmaster | T248 | 1499 |  | Compatibilidad=PS5, PS4, PC; Rotación=900°; Force Feedback=Híbrido; Pedales=3 pedales magnéticos',
        'Logitech G | Driving Force Shifter | 299 |  | Compatibilidad=G29, G920, G923; Tipo=Palanca de 6 velocidades y reversa; Montaje=Abrazadera',
    ],
    'mandos': [
        'Hori | Fighting Stick Alpha | 999 |  | Compatibilidad=PS5, PS4, PC; Tipo=Arcade stick; Botones=Hayabusa; Conectividad=USB',
        'Thrustmaster | T.Flight Hotas 4 | 449 |  | Compatibilidad=PS4, PC; Tipo=Joystick y acelerador de vuelo; Ejes=5; Conectividad=USB',
        'Microsoft | Xbox Adaptive Controller | 499 |  | Compatibilidad=Xbox, PC; Tipo=Control adaptable para accesibilidad; Puertos=19 jacks 3.5 mm; Conectividad=Bluetooth, USB-C',
    ],
    'capturadoras': [
        'Elgato | HD60 X | 699 |  | Entrada=HDMI 4K60 HDR10; Grabación=Hasta 1080p60; Conectividad=USB 3.0; Compatibilidad=PS5, Xbox, Switch',
        'AVerMedia | Live Gamer Mini GC311 | 399 |  | Entrada=HDMI 1080p60; Grabación=1080p60; Conectividad=USB 2.0; Compatibilidad=Consolas y PC',
        'Elgato | 4K X | 999 |  | Entrada=HDMI 2.1 4K144; Grabación=Hasta 4K60 HDR; Conectividad=USB-C 3.2; Compatibilidad=PS5, Xbox, PC',
    ],
    'streaming-gaming': [
        'Elgato | Stream Deck MK.2 | 649 |  | Teclas=15 LCD personalizables; Conectividad=USB-C; Compatibilidad=Windows, macOS; Uso=Atajos y escenas',
        'Elgato | Wave:3 | 599 |  | Tipo=Micrófono condensador; Patrón=Cardioide; Conectividad=USB-C; Resolución=24 bits / 96 kHz',
        'Razer | Kiyo Pro | 499 |  | Resolución=1080p a 60 fps; Sensor=Adaptativo para poca luz; Conectividad=USB 3.0; Campo de visión=103°',
    ],
    # ---------------------------------------------------------------- Periféricos
    'teclados': [
        'Logitech | MX Keys S | 549 |  | Formato=Completo; Teclas=Bajo perfil con retroiluminación; Conectividad=Bluetooth, receptor Logi Bolt; Batería=Hasta 10 días; Compatibilidad=Windows, macOS, Linux',
        'Logitech | K380 Multi-Device | 169 |  | Formato=Compacto; Teclas=Redondeadas silenciosas; Conectividad=Bluetooth; Batería=Hasta 24 meses (2x AAA); Compatibilidad=Windows, macOS, iPadOS, Android',
        'Microsoft | Ergonomic Keyboard | 299 |  | Formato=Ergonómico dividido; Teclas=Con reposamuñecas; Conectividad=USB; Compatibilidad=Windows',
        'Apple | Magic Keyboard con Touch ID | 649 |  | Formato=Compacto; Teclas=Mecanismo de tijera; Conectividad=Bluetooth, USB-C; Batería=Hasta 1 mes; Compatibilidad=Mac con Apple silicon',
    ],
    'mouse': [
        'Logitech | MX Master 3S | 449 | 499 | Sensor=Darkfield 8000 DPI; DPI=Hasta 8000; Conectividad=Bluetooth, receptor Logi Bolt; Batería=Hasta 70 días; Clics=Silenciosos',
        'Logitech | M240 Silent | 89 |  | Sensor=Óptico; DPI=Hasta 4000; Conectividad=Bluetooth; Batería=Hasta 18 meses (1x AA); Clics=Silenciosos',
        'Microsoft | Bluetooth Ergonomic Mouse | 149 |  | Sensor=Óptico; DPI=Hasta 2400; Conectividad=Bluetooth; Batería=Hasta 15 meses; Diseño=Ergonómico',
        'Logitech | Lift Vertical | 349 |  | Sensor=Óptico; DPI=Hasta 4000; Conectividad=Bluetooth, receptor Logi Bolt; Batería=Hasta 24 meses; Diseño=Vertical 57°',
    ],
    'webcams': [
        'Logitech | C920s Pro HD | 299 |  | Resolución=1080p a 30 fps; Micrófono=Estéreo; Enfoque=Automático; Conectividad=USB; Privacidad=Tapa',
        'Logitech | Brio 4K | 899 |  | Resolución=4K a 30 fps / 1080p a 60 fps; Micrófono=Dual omnidireccional; Enfoque=Automático con HDR; Conectividad=USB-C; Privacidad=Tapa',
        'Insta360 | Link 2 | 1299 |  | Resolución=4K a 30 fps; Micrófono=Con reducción de ruido IA; Enfoque=Seguimiento con IA (gimbal); Conectividad=USB-C',
    ],
    'microfonos': [
        'Jabra | Speak2 55 | 699 |  | Tipo=Altavoz con micrófono para conferencias; Micrófonos=4 con cancelación de ruido; Conectividad=Bluetooth, USB-C; Batería=Hasta 12 h',
        'Logitech | Zone Wired Earbuds | 299 |  | Tipo=Audífonos con micrófono para Teams; Micrófonos=Dual con reducción de ruido; Conectividad=USB-C, jack 3.5 mm',
    ],
    'impresoras': [
        'Epson | EcoTank L3250 | 799 |  | Tipo=Multifuncional de tinta continua; Funciones=Imprime, copia y escanea; Velocidad=Hasta 10 ppm; Conectividad=Wi-Fi, Wi-Fi Direct; Rendimiento=Hasta 4500 páginas negro',
        'HP | LaserJet M111w | 549 |  | Tipo=Láser monocromática; Funciones=Imprime; Velocidad=Hasta 20 ppm; Conectividad=Wi-Fi, USB; Rendimiento=Tóner 950 páginas',
        'Brother | DCP-T720DW | 1099 |  | Tipo=Multifuncional de tinta continua; Funciones=Imprime, copia y escanea a doble cara; Velocidad=Hasta 17 ppm; Conectividad=Wi-Fi, USB; Rendimiento=Hasta 7500 páginas negro',
        'Canon | Pixma G3170 | 699 |  | Tipo=Multifuncional de tinta continua; Funciones=Imprime, copia y escanea; Velocidad=Hasta 9 ppm; Conectividad=Wi-Fi; Rendimiento=Hasta 6000 páginas negro',
    ],
    'escaneres': [
        'Epson | WorkForce ES-50 | 599 |  | Tipo=Escáner portátil de hojas; Velocidad=Hasta 10 ppm; Resolución óptica=600 dpi; Conectividad=USB',
        'Fujitsu | ScanSnap iX1600 | 2199 |  | Tipo=Escáner de documentos con alimentador; Velocidad=Hasta 40 ppm doble cara; Resolución óptica=600 dpi; Conectividad=Wi-Fi, USB',
        'Canon | CanoScan LiDE 300 | 349 |  | Tipo=Escáner plano; Resolución óptica=2400 dpi; Formato=A4; Conectividad=USB',
    ],
    'tabletas-digitalizadoras': [
        'Wacom | Intuos Medium Bluetooth | 699 |  | Área activa=216 x 135 mm; Niveles de presión=4096; Conectividad=Bluetooth, USB; Compatibilidad=Windows, macOS, Android',
        'XP-Pen | Artist 13.3 Pro | 1099 |  | Pantalla=13.3" IPS; Resolución=1920x1080; Niveles de presión=8192; Conectividad=USB-C, HDMI',
        'Huion | Inspiroy H640P | 199 |  | Área activa=160 x 100 mm; Niveles de presión=8192; Conectividad=USB; Compatibilidad=Windows, macOS, Android',
    ],
    'docking-stations': [
        'Dell | WD19S USB-C Dock | 899 |  | Conexión=USB-C; Carga=Hasta 130 W; Video=2x DisplayPort, 1x HDMI; Puertos=3x USB-A, 2x USB-C, RJ45',
        'Anker | 778 Thunderbolt Docking Station | 1199 |  | Conexión=Thunderbolt 4; Carga=Hasta 100 W; Video=2x HDMI, 1x DisplayPort; Puertos=6x USB, RJ45, lector SD',
        'Lenovo | ThinkPad Universal USB-C Dock | 799 |  | Conexión=USB-C; Carga=Hasta 90 W; Video=2x DisplayPort, 1x HDMI; Puertos=5x USB, RJ45',
    ],
    'hubs-usb': [
        'Ugreen | Hub USB-C 7 en 1 | 149 |  | Conexión=USB-C; Puertos=HDMI 4K, 3x USB-A 3.0, SD, microSD, USB-C PD 100 W; Material=Aluminio',
        'Anker | 332 USB-C Hub 5 en 1 | 119 |  | Conexión=USB-C; Puertos=HDMI 4K, 2x USB-A, USB-C datos, USB-C PD 100 W; Material=Aluminio',
        'TP-Link | UH400 Hub USB 3.0 4 puertos | 69 |  | Conexión=USB-A 3.0; Puertos=4x USB-A 3.0; Velocidad=5 Gbps',
    ],
    'adaptadores': [
        'Ugreen | Adaptador USB-C a HDMI 4K60 | 79 |  | Entrada=USB-C; Salida=HDMI 2.0; Resolución=Hasta 4K a 60 Hz',
        'Apple | Adaptador USB-C a jack 3.5 mm | 49 |  | Entrada=USB-C; Salida=Jack de audio 3.5 mm; Compatibilidad=iPhone, iPad y Mac con USB-C',
        'StarTech | Adaptador DisplayPort a VGA | 99 |  | Entrada=DisplayPort; Salida=VGA; Resolución=Hasta 1920x1200',
    ],
})

PRODUCTS.update({
    # ---------------------------------------------------------------- Audio y entretenimiento
    'audifonos-bluetooth': [
        'Sony | WH-1000XM5 | 1499 | 1699 | Tipo=Over-ear; Cancelación de ruido=Activa con 8 micrófonos; Conectividad=Bluetooth 5.2, multipunto; Batería=Hasta 30 h; Códecs=LDAC, AAC, SBC',
        'Bose | QuietComfort Ultra Headphones | 1799 |  | Tipo=Over-ear; Cancelación de ruido=Activa con audio inmersivo; Conectividad=Bluetooth 5.3, multipunto; Batería=Hasta 24 h; Códecs=aptX Adaptive, AAC, SBC',
        'JBL | Tune 770NC | 399 |  | Tipo=Over-ear; Cancelación de ruido=Activa adaptativa; Conectividad=Bluetooth 5.3, multipunto; Batería=Hasta 70 h; Códecs=SBC',
        'Sennheiser | Momentum 4 Wireless | 1299 |  | Tipo=Over-ear; Cancelación de ruido=Activa adaptativa; Conectividad=Bluetooth 5.2; Batería=Hasta 60 h; Códecs=aptX Adaptive, AAC, SBC',
    ],
    'audifonos-inalambricos': [
        'Apple | AirPods Pro (2.ª gen) USB-C | 1099 |  | Tipo=In-ear true wireless; Cancelación de ruido=Activa con modo transparencia; Conectividad=Bluetooth 5.3; Batería=Hasta 30 h con estuche; Resistencia=IP54',
        'Samsung | Galaxy Buds2 Pro | 699 | 799 | Tipo=In-ear true wireless; Cancelación de ruido=Activa inteligente; Conectividad=Bluetooth 5.3; Batería=Hasta 29 h con estuche; Resistencia=IPX7',
        'Sony | WF-1000XM5 | 1099 |  | Tipo=In-ear true wireless; Cancelación de ruido=Activa; Conectividad=Bluetooth 5.3; Batería=Hasta 24 h con estuche; Resistencia=IPX4',
        'Xiaomi | Redmi Buds 5 Pro | 299 |  | Tipo=In-ear true wireless; Cancelación de ruido=Activa hasta 52 dB; Conectividad=Bluetooth 5.3; Batería=Hasta 38 h con estuche; Resistencia=IP54',
        'JBL | Wave Beam | 199 |  | Tipo=In-ear true wireless; Cancelación de ruido=Pasiva; Conectividad=Bluetooth 5.2; Batería=Hasta 32 h con estuche; Resistencia=IP54',
    ],
    'audifonos-estudio': [
        'Audio-Technica | ATH-M50x | 649 |  | Tipo=Over-ear cerrado; Drivers=45 mm; Impedancia=38 ohm; Respuesta=15 Hz a 28 kHz; Conectividad=Cable desmontable 3.5 mm',
        'Beyerdynamic | DT 770 Pro 80 ohm | 699 |  | Tipo=Over-ear cerrado; Drivers=45 mm; Impedancia=80 ohm; Respuesta=5 Hz a 35 kHz; Conectividad=Cable fijo 3.5 mm con adaptador 6.3 mm',
        'Sony | MDR-7506 | 499 |  | Tipo=Over-ear cerrado; Drivers=40 mm; Impedancia=63 ohm; Respuesta=10 Hz a 20 kHz; Conectividad=Cable en espiral 3.5 mm',
    ],
    'parlantes': [
        'JBL | Flip 6 | 549 |  | Potencia=30 W; Conectividad=Bluetooth 5.1; Batería=Hasta 12 h; Resistencia=IP67; Función=PartyBoost',
        'JBL | Charge 5 | 699 | 799 | Potencia=40 W; Conectividad=Bluetooth 5.1; Batería=Hasta 20 h; Resistencia=IP67; Función=Power bank integrado',
        'Sony | ULT Field 1 | 449 |  | Potencia=Sonido ULT con graves profundos; Conectividad=Bluetooth 5.3; Batería=Hasta 12 h; Resistencia=IP67',
        'Marshall | Emberton II | 799 |  | Potencia=20 W; Conectividad=Bluetooth 5.1; Batería=Hasta 30 h; Resistencia=IP67; Función=Stack Mode',
    ],
    'barras-sonido': [
        'Samsung | HW-B450 | 899 |  | Canales=2.1; Potencia=300 W; Subwoofer=Inalámbrico; Conectividad=HDMI ARC, óptico, Bluetooth',
        'Sony | HT-S400 | 1199 |  | Canales=2.1; Potencia=330 W; Subwoofer=Inalámbrico; Conectividad=HDMI ARC, óptico, Bluetooth',
        'JBL | Bar 500 | 2299 |  | Canales=5.1; Potencia=590 W; Subwoofer=Inalámbrico de 10"; Conectividad=HDMI eARC, Wi-Fi, Bluetooth; Formato=Dolby Atmos',
    ],
    'dac': [
        'FiiO | K11 | 399 |  | Tipo=DAC y amplificador de escritorio; Resolución=Hasta 384 kHz / 32 bits; Salidas=6.35 mm, 4.4 mm balanceada; Conectividad=USB-C, óptico, coaxial',
        'iFi | Zen DAC V2 | 699 |  | Tipo=DAC y amplificador; Resolución=Hasta 384 kHz y MQA; Salidas=6.35 mm, 4.4 mm balanceada; Conectividad=USB',
        'FiiO | KA13 | 249 |  | Tipo=DAC portátil tipo dongle; Resolución=Hasta 384 kHz / 32 bits; Salidas=3.5 mm, 4.4 mm balanceada; Conectividad=USB-C',
    ],
    'interfaces-audio': [
        'Focusrite | Scarlett Solo 4th Gen | 649 |  | Entradas=1 micrófono XLR, 1 instrumento; Resolución=24 bits / 192 kHz; Conectividad=USB-C; Alimentación=Phantom 48 V',
        'Focusrite | Scarlett 2i2 4th Gen | 899 | 999 | Entradas=2 combo XLR/jack; Resolución=24 bits / 192 kHz; Conectividad=USB-C; Alimentación=Phantom 48 V',
        'Behringer | UMC22 | 249 |  | Entradas=1 micrófono XLR, 1 instrumento; Resolución=16 bits / 48 kHz; Conectividad=USB; Alimentación=Phantom 48 V',
    ],
    'microfonos-usb': [
        'HyperX | QuadCast S | 599 |  | Tipo=Condensador; Patrón=4 patrones polares; Resolución=16 bits / 48 kHz; Conectividad=USB-C; Iluminación=RGB',
        'Blue | Yeti | 549 |  | Tipo=Condensador; Patrón=4 patrones polares; Resolución=16 bits / 48 kHz; Conectividad=USB',
        'Fifine | K669B | 129 |  | Tipo=Condensador; Patrón=Cardioide; Resolución=16 bits / 48 kHz; Conectividad=USB',
        'Rode | NT-USB+ | 799 |  | Tipo=Condensador de estudio; Patrón=Cardioide; Resolución=24 bits / 48 kHz; Conectividad=USB-C',
    ],
    'sistemas-sonido': [
        'Logitech | Z407 Bluetooth 2.1 | 399 |  | Canales=2.1; Potencia=80 W pico; Conectividad=Bluetooth, USB, jack 3.5 mm; Control=Inalámbrico',
        'Edifier | R1280DB | 599 |  | Canales=2.0 activos; Potencia=42 W RMS; Conectividad=Bluetooth, óptico, coaxial, RCA',
        'Creative | Pebble X Plus | 349 |  | Canales=2.1; Potencia=30 W RMS; Conectividad=USB-C, Bluetooth 5.3, jack 3.5 mm; Iluminación=RGB',
    ],
    'reproductores-multimedia': [
        'FiiO | M11S | 1899 |  | Tipo=Reproductor de audio Hi-Res; Pantalla=5" táctil; Almacenamiento=32 GB + microSD; Conectividad=Wi-Fi, Bluetooth 5.0; Batería=Hasta 14 h',
        'Sony | Walkman NW-A306 | 1499 |  | Tipo=Reproductor de audio Hi-Res; Pantalla=3.6" táctil; Almacenamiento=32 GB + microSD; Conectividad=Wi-Fi, Bluetooth 5.0; Batería=Hasta 36 h',
    ],
    # ---------------------------------------------------------------- Redes y conectividad
    'routers': [
        'TP-Link | Archer C6 AC1200 | 149 |  | Estándar=Wi-Fi 5; Velocidad=Hasta 1200 Mbps; Bandas=Doble banda; Puertos=4x LAN Gigabit; Conectividad=Wi-Fi 5',
        'Huawei | WiFi AX2 | 179 |  | Estándar=Wi-Fi 6; Velocidad=Hasta 1500 Mbps; Bandas=Doble banda; Puertos=3x LAN Gigabit; Conectividad=Wi-Fi 6',
        'Mercusys | MR30G AC1200 | 99 |  | Estándar=Wi-Fi 5; Velocidad=Hasta 1200 Mbps; Bandas=Doble banda; Puertos=2x LAN Gigabit; Conectividad=Wi-Fi 5',
    ],
    'routers-wifi-6-7': [
        'TP-Link | Archer AX55 AX3000 | 329 |  | Estándar=Wi-Fi 6; Velocidad=Hasta 3000 Mbps; Bandas=Doble banda; Puertos=4x LAN Gigabit, USB 3.0; Conectividad=Wi-Fi 6',
        'ASUS | RT-AX86U Pro | 1199 |  | Estándar=Wi-Fi 6; Velocidad=Hasta 5700 Mbps; Bandas=Doble banda; Puertos=2.5G WAN/LAN, 4x LAN Gigabit; Conectividad=Wi-Fi 6',
        'TP-Link | Archer BE550 BE9300 | 1499 | 1699 | Estándar=Wi-Fi 7; Velocidad=Hasta 9300 Mbps; Bandas=Tribanda; Puertos=5x 2.5G; Conectividad=Wi-Fi 7',
        'Netgear | Nighthawk RS300 | 1999 |  | Estándar=Wi-Fi 7; Velocidad=Hasta 9300 Mbps; Bandas=Tribanda; Puertos=10G WAN, 4x LAN; Conectividad=Wi-Fi 7',
    ],
    'sistemas-mesh': [
        'TP-Link | Deco X20 (pack de 3) | 899 |  | Estándar=Wi-Fi 6; Cobertura=Hasta 540 m²; Velocidad=Hasta 1800 Mbps; Nodos=3; Conectividad=Wi-Fi 6',
        'Google | Nest Wifi Pro (pack de 2) | 1399 |  | Estándar=Wi-Fi 6E; Cobertura=Hasta 400 m²; Velocidad=Hasta 5400 Mbps; Nodos=2; Conectividad=Wi-Fi 6E',
        'Eero | 6+ (pack de 3) | 1299 |  | Estándar=Wi-Fi 6; Cobertura=Hasta 420 m²; Velocidad=Hasta 1000 Mbps; Nodos=3; Conectividad=Wi-Fi 6',
    ],
    'switches': [
        'TP-Link | TL-SG108 8 puertos Gigabit | 99 |  | Puertos=8x Gigabit; Gestión=No administrable; Carcasa=Metálica; Conectividad=Ethernet',
        'Netgear | GS308PP 8 puertos PoE+ | 449 |  | Puertos=8x Gigabit PoE+; Presupuesto PoE=83 W; Gestión=No administrable; Conectividad=Ethernet, PoE',
        'TP-Link | TL-SG105 5 puertos Gigabit | 59 |  | Puertos=5x Gigabit; Gestión=No administrable; Carcasa=Metálica; Conectividad=Ethernet',
    ],
    'access-points': [
        'TP-Link | Omada EAP650 AX3000 | 549 |  | Estándar=Wi-Fi 6; Velocidad=Hasta 3000 Mbps; Alimentación=PoE+; Gestión=Omada SDN; Conectividad=Wi-Fi 6',
        'Ubiquiti | UniFi U6+ | 699 |  | Estándar=Wi-Fi 6; Velocidad=Hasta 3000 Mbps; Alimentación=PoE; Gestión=UniFi Network; Conectividad=Wi-Fi 6',
        'Ubiquiti | UniFi U7 Pro | 1199 |  | Estándar=Wi-Fi 7; Velocidad=Hasta 9300 Mbps; Alimentación=PoE+; Gestión=UniFi Network; Conectividad=Wi-Fi 7',
    ],
    'adaptadores-red': [
        'TP-Link | UE306 USB 3.0 a Gigabit | 69 |  | Interfaz=USB 3.0; Velocidad=Gigabit Ethernet; Compatibilidad=Windows, macOS, Linux; Conectividad=Ethernet',
        'TP-Link | Archer T3U Plus AC1300 | 99 |  | Interfaz=USB 3.0; Velocidad=Hasta 1300 Mbps; Antena=Alta ganancia; Conectividad=Wi-Fi 5',
        'Ugreen | Adaptador USB-C a 2.5G Ethernet | 129 |  | Interfaz=USB-C; Velocidad=2.5 Gbps; Compatibilidad=Windows, macOS, Linux; Conectividad=Ethernet',
    ],
    'tarjetas-wifi': [
        'TP-Link | Archer TXE75E AXE5400 | 299 |  | Estándar=Wi-Fi 6E; Velocidad=Hasta 5400 Mbps; Interfaz=PCIe; Bluetooth=5.3; Conectividad=Wi-Fi 6E',
        'Intel | AX210 M.2 con antenas | 149 |  | Estándar=Wi-Fi 6E; Velocidad=Hasta 2400 Mbps; Interfaz=M.2 2230; Bluetooth=5.3; Conectividad=Wi-Fi 6E',
    ],
    'cables-ethernet': [
        'Ugreen | Cable Cat 6 plano 5 m | 39 |  | Categoría=Cat 6; Longitud=5 m; Velocidad=Hasta 1 Gbps; Conectividad=Ethernet',
        'Belkin | Cable Cat 8 S/FTP 3 m | 89 |  | Categoría=Cat 8; Longitud=3 m; Velocidad=Hasta 40 Gbps; Conectividad=Ethernet',
        'TP-Link | Bobina Cat 6 UTP 305 m | 649 |  | Categoría=Cat 6; Longitud=305 m; Velocidad=Hasta 1 Gbps; Conectividad=Ethernet',
    ],
    'fibra-optica': [
        'TP-Link | XZ000-G3 ONT GPON | 199 |  | Tipo=ONT GPON; Puertos=1x LAN Gigabit; Conectividad=Fibra óptica, Ethernet',
        'Ubiquiti | UF-SM-10G Módulo SFP+ | 149 |  | Tipo=Módulo SFP+ monomodo; Velocidad=10 Gbps; Alcance=Hasta 10 km; Conectividad=Fibra óptica',
        'FS | Cable de fibra LC-LC dúplex OM4 3 m | 49 |  | Tipo=Patch cord multimodo OM4; Conectores=LC/UPC; Longitud=3 m; Conectividad=Fibra óptica',
    ],
    'accesorios-conectividad': [
        'TP-Link | TL-PA7017 KIT Powerline AV1000 | 249 |  | Tipo=Adaptadores Powerline; Velocidad=Hasta 1000 Mbps; Puertos=1x Gigabit por adaptador; Conectividad=Ethernet',
        'Klein Tools | Crimpadora RJ45 con probador | 199 |  | Tipo=Kit de crimpado y prueba; Compatibilidad=RJ45, RJ11; Incluye=Probador de continuidad',
        'Ugreen | Caja de conectores RJ45 Cat 6 (50 unidades) | 49 |  | Tipo=Conectores RJ45; Categoría=Cat 6; Unidades=50',
    ],
    # ---------------------------------------------------------------- Almacenamiento
    'ssd-externos': [
        'Samsung | T7 Shield 1TB | 449 | 499 | Capacidad=1 TB; Velocidad=Hasta 1050 MB/s; Conectividad=USB-C 3.2 Gen 2; Resistencia=IP65; Peso=98 g',
        'SanDisk | Extreme Portable SSD V2 2TB | 699 |  | Capacidad=2 TB; Velocidad=Hasta 1050 MB/s; Conectividad=USB-C 3.2 Gen 2; Resistencia=IP65; Peso=52 g',
        'Crucial | X9 Pro 1TB | 399 |  | Capacidad=1 TB; Velocidad=Hasta 1050 MB/s; Conectividad=USB-C 3.2 Gen 2; Resistencia=IP55; Peso=38 g',
        'Samsung | T9 4TB | 1599 |  | Capacidad=4 TB; Velocidad=Hasta 2000 MB/s; Conectividad=USB-C 3.2 Gen 2x2; Resistencia=Caídas de 3 m; Peso=122 g',
    ],
    'hdd-externos': [
        'WD | My Passport 2TB | 299 |  | Capacidad=2 TB; Velocidad=USB 3.0; Conectividad=USB-A 3.0; Seguridad=Cifrado AES 256 bits',
        'Seagate | Expansion 4TB | 449 |  | Capacidad=4 TB; Velocidad=USB 3.0; Conectividad=USB-A 3.0; Formato=2.5" portátil',
        'WD | Elements Desktop 12TB | 1099 |  | Capacidad=12 TB; Velocidad=USB 3.0; Conectividad=USB-A 3.0; Formato=3.5" de escritorio',
    ],
    'memorias-usb': [
        'SanDisk | Ultra Flair 128GB USB 3.0 | 49 |  | Capacidad=128 GB; Velocidad=Hasta 150 MB/s; Conectividad=USB-A 3.0; Material=Metal',
        'Kingston | DataTraveler Exodia 64GB | 25 |  | Capacidad=64 GB; Velocidad=USB 3.2 Gen 1; Conectividad=USB-A; Material=Plástico con capuchón',
        'Samsung | BAR Plus 256GB | 129 |  | Capacidad=256 GB; Velocidad=Hasta 400 MB/s; Conectividad=USB-A 3.1; Material=Metal resistente al agua',
        'SanDisk | Dual Drive Luxe USB-C 256GB | 149 |  | Capacidad=256 GB; Velocidad=Hasta 400 MB/s; Conectividad=USB-C y USB-A; Material=Metal',
    ],
    'tarjetas-microsd': [
        'SanDisk | Extreme microSDXC 256GB A2 | 129 |  | Capacidad=256 GB; Velocidad=Hasta 190 MB/s lectura; Clase=UHS-I U3 V30 A2; Uso=Cámaras 4K y drones',
        'Samsung | EVO Plus microSDXC 128GB | 59 |  | Capacidad=128 GB; Velocidad=Hasta 160 MB/s lectura; Clase=UHS-I U3 V30 A2; Uso=Smartphones y tablets',
        'Kingston | Canvas Go! Plus 512GB | 199 |  | Capacidad=512 GB; Velocidad=Hasta 170 MB/s lectura; Clase=UHS-I U3 V30 A2; Uso=Cámaras y consolas',
    ],
    'nas': [
        'Synology | DiskStation DS224+ | 1699 |  | Bahías=2 (sin discos); Procesador=Intel Celeron J4125; RAM=2 GB DDR4; Conectividad=2x LAN Gigabit; Formato=Torre',
        'QNAP | TS-264 | 1899 |  | Bahías=2 (sin discos); Procesador=Intel Celeron N5105; RAM=8 GB DDR4; Conectividad=2x 2.5 GbE; Formato=Torre',
        'Synology | DiskStation DS423+ | 2799 |  | Bahías=4 (sin discos); Procesador=Intel Celeron J4125; RAM=2 GB DDR4; Conectividad=2x LAN Gigabit; Formato=Torre',
    ],
    'unidades-respaldo': [
        'WD | My Book Duo 16TB | 1999 |  | Capacidad=16 TB (2 discos); Modos=RAID 0 / RAID 1; Conectividad=USB-C 3.2; Software=Copias automáticas',
        'Seagate | One Touch Hub 8TB | 799 |  | Capacidad=8 TB; Conectividad=USB-C, USB-A frontal; Software=Copias automáticas',
    ],
    'accesorios-almacenamiento': [
        'Ugreen | Carcasa para SSD M.2 NVMe USB-C 10 Gbps | 119 |  | Compatibilidad=SSD M.2 NVMe 2230 a 2280; Velocidad=10 Gbps; Material=Aluminio sin herramientas; Conectividad=USB-C',
        'Orico | Docking station para 2 discos SATA | 199 |  | Compatibilidad=Discos 2.5" y 3.5"; Función=Clonación sin PC; Conectividad=USB 3.0',
        'Ugreen | Lector de tarjetas SD y microSD USB-C | 49 |  | Compatibilidad=SD, microSD; Velocidad=USB 3.0; Conectividad=USB-C y USB-A',
    ],
    # ---------------------------------------------------------------- Smart Home
    'focos-inteligentes': [
        'Philips Hue | White and Color Ambiance E27 | 199 |  | Casquillo=E27; Potencia=9 W; Color=16 millones de colores; Conectividad=Zigbee, Bluetooth; Compatibilidad=Alexa, Google Home, Apple Home',
        'TP-Link | Tapo L530E | 59 |  | Casquillo=E27; Potencia=8.7 W; Color=16 millones de colores; Conectividad=Wi-Fi 2.4 GHz; Compatibilidad=Alexa, Google Home',
        'Xiaomi | Smart LED Bulb Essential | 49 |  | Casquillo=E27; Potencia=9 W; Color=Blanco y colores; Conectividad=Wi-Fi 2.4 GHz; Compatibilidad=Alexa, Google Home',
    ],
    'enchufes-inteligentes': [
        'TP-Link | Tapo P110 con medidor de consumo | 59 |  | Potencia máxima=16 A; Función=Medición de consumo y programación; Conectividad=Wi-Fi 2.4 GHz; Compatibilidad=Alexa, Google Home',
        'Eve | Energy Matter | 179 |  | Potencia máxima=16 A; Función=Medición de consumo; Conectividad=Thread, Matter; Compatibilidad=Apple Home, Google Home, Alexa',
    ],
    'camaras-inteligentes': [
        'TP-Link | Tapo C210 2K | 129 |  | Resolución=2304x1296; Visión nocturna=Hasta 9 m; Movimiento=Rotación 360°; Conectividad=Wi-Fi 2.4 GHz; Almacenamiento=microSD hasta 512 GB',
        'Xiaomi | Smart Camera C400 | 169 |  | Resolución=2560x1440; Visión nocturna=Infrarroja; Movimiento=Rotación 360°; Conectividad=Wi-Fi 2.4/5 GHz; Almacenamiento=microSD hasta 256 GB',
        'Ezviz | C8C exterior | 299 |  | Resolución=1920x1080; Visión nocturna=A color hasta 30 m; Movimiento=Pan 352° e inclinación 95°; Conectividad=Wi-Fi 2.4 GHz; Resistencia=IP65',
    ],
    'sensores': [
        'Aqara | Sensor de puertas y ventanas P2 | 99 |  | Tipo=Contacto magnético; Conectividad=Thread, Matter; Batería=Hasta 2 años; Compatibilidad=Apple Home, Google Home, Alexa',
        'Aqara | Sensor de temperatura y humedad T1 | 79 |  | Tipo=Temperatura, humedad y presión; Conectividad=Zigbee 3.0; Batería=Hasta 2 años; Compatibilidad=Hub Aqara',
        'Philips Hue | Sensor de movimiento interior | 199 |  | Tipo=Movimiento y luz ambiental; Conectividad=Zigbee; Batería=Hasta 2 años; Compatibilidad=Hue Bridge',
    ],
    'asistentes-inteligentes': [
        'Amazon | Echo Dot 5.ª gen | 249 |  | Tipo=Parlante inteligente; Asistente=Alexa; Conectividad=Wi-Fi, Bluetooth; Función=Sensor de temperatura',
        'Google | Nest Hub 2.ª gen | 449 |  | Tipo=Pantalla inteligente de 7"; Asistente=Google Assistant; Conectividad=Wi-Fi, Bluetooth, Thread; Función=Control del hogar',
        'Amazon | Echo Show 8 3.ª gen | 699 |  | Tipo=Pantalla inteligente de 8"; Asistente=Alexa; Conectividad=Wi-Fi, Bluetooth, Zigbee, Matter; Función=Videollamadas',
    ],
    'cerraduras-inteligentes': [
        'Xiaomi | Smart Door Lock M20 | 1199 |  | Desbloqueo=Huella, clave, NFC y app; Conectividad=Bluetooth, Wi-Fi; Batería=8x AA hasta 12 meses; Alarma=Antimanipulación',
        'Yale | Assure Lock 2 Touch | 1499 |  | Desbloqueo=Huella, clave y app; Conectividad=Bluetooth, Wi-Fi con módulo; Batería=4x AA; Alarma=Antimanipulación',
    ],
    'controladores-smart': [
        'Aqara | Hub M3 Matter | 499 |  | Protocolos=Zigbee 3.0, Thread, Bluetooth, IR; Conectividad=Wi-Fi, Ethernet, Matter; Función=Automatizaciones locales',
        'Philips Hue | Bridge | 299 |  | Protocolos=Zigbee; Dispositivos=Hasta 50 luces; Conectividad=Ethernet, Matter; Compatibilidad=Alexa, Google Home, Apple Home',
        'SwitchBot | Hub 2 | 329 |  | Protocolos=Bluetooth, IR, Matter; Conectividad=Wi-Fi 2.4 GHz; Función=Control de aire acondicionado y TV por IR; Sensor=Temperatura y humedad',
    ],
    'automatizacion': [
        'SwitchBot | Bot pulsador inteligente | 129 |  | Función=Pulsa interruptores existentes; Conectividad=Bluetooth (Wi-Fi con hub); Batería=Hasta 600 días',
        'Sonoff | Mini R4 Wi-Fi Smart Switch | 59 |  | Función=Interruptor inteligente para empotrar; Potencia máxima=10 A; Conectividad=Wi-Fi 2.4 GHz',
        'SwitchBot | Curtain 3 | 399 |  | Función=Motor para cortinas de riel; Conectividad=Bluetooth (Wi-Fi con hub); Batería=Hasta 8 meses',
    ],
    # ---------------------------------------------------------------- Seguridad electrónica
    'camaras-ip': [
        'Hikvision | DS-2CD1043G2-I 4MP | 249 |  | Resolución=2560x1440; Visión nocturna=Infrarroja hasta 30 m; Alimentación=PoE; Resistencia=IP67; Conectividad=Ethernet, PoE',
        'Dahua | IPC-HFW2441S-S 4MP WizSense | 329 |  | Resolución=2688x1520; Visión nocturna=Infrarroja hasta 30 m; Alimentación=PoE; Resistencia=IP67; Conectividad=Ethernet, PoE',
        'Reolink | RLC-810A 4K | 449 |  | Resolución=3840x2160; Visión nocturna=Infrarroja hasta 30 m; Alimentación=PoE; Resistencia=IP66; Conectividad=Ethernet, PoE',
    ],
    'cctv': [
        'Hikvision | Kit Turbo HD 4 cámaras 1080p | 899 |  | Cámaras=4 tipo bala 1080p; Grabador=DVR 4 canales; Almacenamiento=Disco no incluido; Conectividad=Ethernet',
        'Dahua | Kit Cooper 8 cámaras 1080p | 1499 |  | Cámaras=8 1080p; Grabador=DVR 8 canales; Almacenamiento=Disco no incluido; Conectividad=Ethernet',
    ],
    'nvr': [
        'Hikvision | DS-7608NXI-K1/8P NVR 8 canales PoE | 1099 |  | Canales=8 IP; Resolución=Hasta 8 MP; Puertos=8x PoE; Almacenamiento=1 disco SATA hasta 10 TB; Conectividad=Ethernet, PoE',
        'Reolink | RLN8-410 NVR 8 canales | 1299 |  | Canales=8 IP; Resolución=Hasta 4K; Puertos=8x PoE; Almacenamiento=Disco de 2 TB incluido; Conectividad=Ethernet, PoE',
    ],
    'videoporteros': [
        'Ring | Video Doorbell Plus | 599 |  | Resolución=1536x1536; Visión nocturna=Infrarroja; Audio=Bidireccional; Conectividad=Wi-Fi 2.4 GHz; Alimentación=Batería recargable',
        'Ezviz | DB2 Pro | 549 |  | Resolución=2560x1920; Visión nocturna=Infrarroja; Audio=Bidireccional; Conectividad=Wi-Fi 2.4 GHz; Alimentación=Batería con timbre inalámbrico',
    ],
    'alarmas': [
        'Ajax | StarterKit | 1299 |  | Incluye=Hub, sensor de movimiento, contacto y control; Conectividad=Ethernet, GSM; Alcance=Hasta 2000 m; Aviso=App y sirena',
        'Xiaomi | Kit de alarma inteligente | 399 |  | Incluye=Hub, 2 sensores de puerta y sensor de movimiento; Conectividad=Wi-Fi, Zigbee; Aviso=App y sirena integrada',
    ],
    'sensores-movimiento': [
        'Ajax | MotionProtect | 249 |  | Tipo=PIR inalámbrico; Alcance=Hasta 12 m; Inmunidad a mascotas=Hasta 20 kg; Batería=Hasta 5 años',
        'Hikvision | DS-PDP15P-EG2-WE | 199 |  | Tipo=PIR inalámbrico; Alcance=Hasta 15 m; Inmunidad a mascotas=Hasta 30 kg; Batería=Hasta 5 años',
    ],
    'control-acceso': [
        'ZKTeco | SpeedFace-V5L | 1299 |  | Verificación=Rostro, palma, huella y tarjeta; Usuarios=Hasta 6000 rostros; Conectividad=Ethernet, Wi-Fi; Uso=Control de asistencia y acceso',
        'Hikvision | DS-K1T341AMF | 899 |  | Verificación=Rostro, huella y tarjeta; Usuarios=Hasta 1500 rostros; Conectividad=Ethernet, Wi-Fi; Pantalla=4.3" táctil',
    ],
    # ---------------------------------------------------------------- Televisores
    'smart-tv': [
        'Samsung | Crystal UHD 55DU7000 55" | 1899 | 2199 | Pantalla=55" LED; Resolución=3840x2160; Frecuencia=60 Hz; Sistema=Tizen; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI',
        'LG | 50UR8750PSA 50" | 1599 |  | Pantalla=50" LED; Resolución=3840x2160; Frecuencia=60 Hz; Sistema=webOS; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI',
        'Xiaomi | TV A Pro 43" | 1199 |  | Pantalla=43" LED; Resolución=3840x2160; Frecuencia=60 Hz; Sistema=Google TV; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI',
        'TCL | 32S5400A 32" | 699 |  | Pantalla=32" LED; Resolución=1366x768; Frecuencia=60 Hz; Sistema=Android TV; Conectividad=Wi-Fi 4, Bluetooth, 2x HDMI',
    ],
    'oled': [
        'LG | OLED evo C4 55" | 5999 |  | Pantalla=55" OLED evo; Resolución=3840x2160; Frecuencia=144 Hz; Sistema=webOS; Conectividad=Wi-Fi 6, Bluetooth, 4x HDMI 2.1',
        'Sony | Bravia 8 XR 65" | 8999 |  | Pantalla=65" OLED; Resolución=3840x2160; Frecuencia=120 Hz; Sistema=Google TV; Conectividad=Wi-Fi 5, Bluetooth, 4x HDMI',
        'Samsung | OLED S90D 55" | 5499 | 5999 | Pantalla=55" QD-OLED; Resolución=3840x2160; Frecuencia=144 Hz; Sistema=Tizen; Conectividad=Wi-Fi 5, Bluetooth, 4x HDMI 2.1',
    ],
    'qled': [
        'Samsung | QLED Q60D 65" | 3299 |  | Pantalla=65" QLED; Resolución=3840x2160; Frecuencia=60 Hz; Sistema=Tizen; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI',
        'TCL | QLED C655 55" | 1999 |  | Pantalla=55" QLED; Resolución=3840x2160; Frecuencia=60 Hz; Sistema=Google TV; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI',
        'Hisense | QLED E7N 65" | 2799 |  | Pantalla=65" QLED; Resolución=3840x2160; Frecuencia=144 Hz; Sistema=VIDAA; Conectividad=Wi-Fi 5, Bluetooth, 3x HDMI 2.1',
    ],
    'mini-led': [
        'TCL | Mini LED C855 65" | 5499 |  | Pantalla=65" Mini LED QLED; Resolución=3840x2160; Frecuencia=144 Hz; Sistema=Google TV; Conectividad=Wi-Fi 6, Bluetooth, 4x HDMI 2.1',
        'Samsung | Neo QLED QN85D 55" | 4799 |  | Pantalla=55" Neo QLED Mini LED; Resolución=3840x2160; Frecuencia=120 Hz; Sistema=Tizen; Conectividad=Wi-Fi 5, Bluetooth, 4x HDMI 2.1',
        'Hisense | U8N Mini LED 65" | 4999 |  | Pantalla=65" Mini LED Pro; Resolución=3840x2160; Frecuencia=144 Hz; Sistema=Google TV; Conectividad=Wi-Fi 6, Bluetooth, 4x HDMI 2.1',
    ],
    'proyectores': [
        'Epson | Home Cinema 2350 4K PRO-UHD | 6499 |  | Resolución=3840x2160 (PRO-UHD); Brillo=2800 lúmenes; Tecnología=3LCD; Conectividad=Wi-Fi, 2x HDMI; Sistema=Android TV',
        'XGIMI | Halo+ | 3299 |  | Resolución=1920x1080; Brillo=900 lúmenes ANSI; Tecnología=DLP portátil; Conectividad=Wi-Fi, Bluetooth, HDMI; Batería=Hasta 2.5 h',
        'BenQ | TH575 | 2299 |  | Resolución=1920x1080; Brillo=3800 lúmenes; Tecnología=DLP; Conectividad=2x HDMI, USB',
    ],
    'tv-box': [
        'Xiaomi | TV Box S 2.ª gen | 249 |  | Resolución=Hasta 4K HDR; Sistema=Google TV; Almacenamiento=8 GB; Conectividad=Wi-Fi 5, Bluetooth 5.2, HDMI',
        'Nvidia | Shield TV Pro | 899 |  | Resolución=Hasta 4K HDR con escalado IA; Sistema=Android TV; Almacenamiento=16 GB; Conectividad=Wi-Fi 5, Ethernet Gigabit, 2x USB 3.0',
    ],
    'streaming': [
        'Google | Chromecast con Google TV 4K | 299 |  | Resolución=Hasta 4K HDR; Sistema=Google TV; Conectividad=Wi-Fi 5, Bluetooth, HDMI; Control=Con asistente de voz',
        'Amazon | Fire TV Stick 4K Max | 299 |  | Resolución=Hasta 4K HDR; Sistema=Fire OS; Conectividad=Wi-Fi 6E, Bluetooth, HDMI; Control=Alexa',
        'Apple | TV 4K 128GB Wi-Fi + Ethernet | 849 |  | Resolución=Hasta 4K HDR y Dolby Vision; Sistema=tvOS; Almacenamiento=128 GB; Conectividad=Wi-Fi 6, Ethernet, Thread',
    ],
    'accesorios-video': [
        'Vivanco | Rack de pared para TV 32" a 65" | 149 |  | Compatibilidad=TV de 32" a 65"; Carga=Hasta 40 kg; VESA=Hasta 400x400; Tipo=Inclinable',
        'Ugreen | Cable HDMI 2.1 8K 2 m | 79 |  | Versión=HDMI 2.1; Resolución=Hasta 8K a 60 Hz / 4K a 120 Hz; Longitud=2 m; Velocidad=48 Gbps',
        'Logitech | Harmony Companion (control universal) | 449 |  | Dispositivos=Hasta 8; Conectividad=IR, Bluetooth, Wi-Fi; Compatibilidad=Alexa, Google Home',
    ],
})

PRODUCTS.update({
    # ---------------------------------------------------------------- Fotografía y creación de contenido
    'camaras-digitales': [
        'Sony | ZV-1 II | 3299 |  | Sensor=1" 20.1 MP; Lente=18-50 mm equivalente; Video=4K a 30 fps; Pantalla=3" abatible; Conectividad=Wi-Fi, Bluetooth, USB-C',
        'Canon | PowerShot G7 X Mark III | 3499 |  | Sensor=1" 20.1 MP; Lente=24-100 mm f/1.8-2.8; Video=4K a 30 fps; Pantalla=3" táctil abatible; Conectividad=Wi-Fi, Bluetooth',
        'GoPro | HERO12 Black | 1799 | 1999 | Sensor=1/1.9" 27 MP; Video=5.3K a 60 fps; Estabilización=HyperSmooth 6.0; Resistencia=Sumergible 10 m; Conectividad=Wi-Fi, Bluetooth',
    ],
    'camaras-mirrorless': [
        'Sony | Alpha a6400 con lente 16-50 mm | 3799 |  | Sensor=APS-C 24.2 MP; Enfoque=425 puntos con Eye AF; Video=4K a 30 fps; Montura=Sony E; Conectividad=Wi-Fi, Bluetooth',
        'Canon | EOS R50 con lente RF-S 18-45 mm | 3299 |  | Sensor=APS-C 24.2 MP; Enfoque=Dual Pixel CMOS AF II; Video=4K a 30 fps; Montura=Canon RF; Conectividad=Wi-Fi, Bluetooth',
        'Nikon | Z6 III cuerpo | 10499 |  | Sensor=Full frame 24.5 MP parcialmente apilado; Enfoque=273 puntos; Video=6K a 60 fps RAW; Montura=Nikon Z; Conectividad=Wi-Fi, Bluetooth',
        'Fujifilm | X-T30 II con lente 15-45 mm | 4199 |  | Sensor=APS-C X-Trans 26.1 MP; Enfoque=425 puntos; Video=4K a 30 fps; Montura=Fujifilm X; Conectividad=Wi-Fi, Bluetooth',
    ],
    'lentes': [
        'Sony | FE 50 mm f/1.8 | 899 |  | Montura=Sony E; Distancia focal=50 mm; Apertura=f/1.8; Formato=Full frame; Peso=186 g',
        'Sigma | 18-50 mm f/2.8 DC DN Contemporary | 2199 |  | Montura=Sony E; Distancia focal=18-50 mm; Apertura=f/2.8; Formato=APS-C; Peso=290 g',
        'Canon | RF 24-105 mm f/4-7.1 IS STM | 1899 |  | Montura=Canon RF; Distancia focal=24-105 mm; Apertura=f/4-7.1; Formato=Full frame; Estabilización=5 pasos',
    ],
    'tripodes': [
        'Manfrotto | Befree Advanced | 899 |  | Altura máxima=150 cm; Carga=Hasta 8 kg; Material=Aluminio; Rótula=Bola con placa Arca',
        'Ulanzi | MT-44 trípode extensible | 129 |  | Altura máxima=150 cm; Carga=Hasta 1.5 kg; Material=Aluminio; Uso=Smartphones y cámaras ligeras',
        'Joby | GorillaPod 3K Kit | 349 |  | Altura máxima=30 cm; Carga=Hasta 3 kg; Material=Patas flexibles; Rótula=Bola incluida',
    ],
    'iluminacion-led': [
        'Elgato | Key Light | 899 |  | Potencia=45 W; Brillo=2800 lúmenes; Temperatura=2900 a 7000 K; Control=App por Wi-Fi',
        'Neewer | Kit de 2 paneles LED 660 bicolor | 599 |  | Potencia=2x 40 W; Temperatura=3200 a 5600 K; Control=Pantalla LCD; Incluye=Trípodes',
        'Ulanzi | VL49 RGB mini | 89 |  | Potencia=6 W; Temperatura=2500 a 9000 K y RGB; Batería=2000 mAh; Montaje=Zapata fría',
    ],
    'microfonos-contenido': [
        'Rode | Wireless GO II | 1299 |  | Tipo=Sistema inalámbrico doble; Alcance=Hasta 200 m; Grabación=Interna de 24 h; Conectividad=USB-C, 3.5 mm',
        'DJI | Mic 2 (2 TX + 1 RX) | 1299 |  | Tipo=Sistema inalámbrico doble; Alcance=Hasta 250 m; Grabación=32 bits flotante interna; Conectividad=USB-C, Lightning, 3.5 mm',
        'Rode | VideoMicro II | 349 |  | Tipo=Micrófono de cañón para cámara; Patrón=Supercardioide; Conectividad=3.5 mm; Montaje=Zapata fría',
    ],
    'estabilizadores': [
        'DJI | Osmo Mobile 6 | 649 |  | Tipo=Gimbal para smartphone; Ejes=3; Batería=Hasta 6 h; Función=Seguimiento ActiveTrack 6.0',
        'DJI | RS 4 Mini | 1599 |  | Tipo=Gimbal para cámara; Ejes=3; Carga=Hasta 2 kg; Batería=Hasta 13 h',
        'Zhiyun | Crane-M3S | 1199 |  | Tipo=Gimbal para cámara y smartphone; Ejes=3; Carga=Hasta 1.2 kg; Batería=Hasta 10 h',
    ],
    'capturadoras-video': [
        'Elgato | Cam Link 4K | 549 |  | Entrada=HDMI hasta 4K30; Salida=USB 3.0; Uso=Cámara como webcam; Compatibilidad=Windows, macOS',
        'Ugreen | Capturadora HDMI USB 4K | 99 |  | Entrada=HDMI hasta 4K; Grabación=1080p a 60 fps; Salida=USB 3.0; Compatibilidad=Windows, macOS, Linux',
    ],
    'equipos-streaming': [
        'Blackmagic | ATEM Mini Pro | 2299 |  | Entradas=4x HDMI; Transmisión=Directa por Ethernet; Grabación=USB a disco; Funciones=Multivista y chroma key',
        'Rode | RodeCaster Duo | 2799 |  | Entradas=2 micrófonos XLR; Funciones=Mezcla de podcast con pads; Conectividad=USB-C, Bluetooth; Grabación=microSD',
    ],
    'drones': [
        'DJI | Mini 4 Pro | 4299 |  | Cámara=48 MP 1/1.3"; Video=4K a 100 fps HDR; Peso=Menos de 249 g; Autonomía=Hasta 34 min; Alcance=Hasta 20 km',
        'DJI | Neo | 999 |  | Cámara=12 MP; Video=4K a 30 fps; Peso=135 g; Autonomía=Hasta 18 min; Función=Despegue desde la palma',
        'DJI | Air 3 | 5899 |  | Cámara=Doble 48 MP; Video=4K a 100 fps HDR; Peso=720 g; Autonomía=Hasta 46 min; Alcance=Hasta 20 km',
    ],
    # ---------------------------------------------------------------- Tecnología empresarial
    'servidores': [
        'Dell | PowerEdge T150 | 6999 |  | Procesador=Intel Xeon E-2314; RAM=16 GB DDR4 ECC; Almacenamiento=2 TB HDD; Formato=Torre; Gestión=iDRAC9',
        'HPE | ProLiant DL20 Gen10 Plus | 8999 |  | Procesador=Intel Xeon E-2336; RAM=16 GB DDR4 ECC; Almacenamiento=Sin discos (4 bahías); Formato=Rack 1U; Gestión=iLO 5',
        'Lenovo | ThinkSystem ST50 V2 | 5999 |  | Procesador=Intel Xeon E-2324G; RAM=16 GB DDR4 ECC; Almacenamiento=1 TB HDD; Formato=Torre; Gestión=XClarity',
    ],
    'redes-empresariales': [
        'Cisco | CBS250-24T-4G | 1599 |  | Puertos=24x Gigabit + 4x SFP; Gestión=Administrable L2+; Montaje=Rack 1U; Conectividad=Ethernet',
        'Ubiquiti | UniFi Cloud Gateway Ultra | 699 |  | Tipo=Gateway con firewall; Rendimiento=1 Gbps con IDS/IPS; Puertos=4x LAN, 1x WAN 2.5G; Gestión=UniFi Network',
        'MikroTik | RB5009UG+S+IN | 899 |  | Tipo=Router empresarial; Puertos=7x Gigabit, 1x 2.5G, 1x SFP+; Gestión=RouterOS; Montaje=Escritorio o rack',
    ],
    'ups-empresarial': [
        'APC | Smart-UPS SMT1500RMI2U | 4999 |  | Capacidad=1500 VA / 1000 W; Tipo=Línea interactiva onda sinusoidal pura; Formato=Rack 2U; Tomas=4x IEC C13; Gestión=SmartConnect',
        'Eaton | 5PX G2 2200 | 6499 |  | Capacidad=2200 VA / 2200 W; Tipo=Línea interactiva onda sinusoidal pura; Formato=Rack/torre 2U; Tomas=8x IEC C13; Gestión=Tarjeta de red opcional',
    ],
    'nas-empresarial': [
        'Synology | RackStation RS1221+ | 6999 |  | Bahías=8 (sin discos); Procesador=AMD Ryzen V1500B; RAM=4 GB DDR4 ECC; Conectividad=4x LAN Gigabit; Formato=Rack 2U',
        'QNAP | TS-873A | 5499 |  | Bahías=8 (sin discos); Procesador=AMD Ryzen V1500B; RAM=8 GB DDR4; Conectividad=2x 2.5 GbE; Formato=Torre',
    ],
    'estaciones-trabajo': [
        'Dell | Precision 3680 Tower | 8999 |  | Procesador=Intel Core i7-14700; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; GPU=NVIDIA RTX A2000 12 GB; Formato=Torre; Sistema=Windows 11 Pro',
        'HP | Z2 Tower G9 | 9999 |  | Procesador=Intel Core i9-13900K; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; GPU=NVIDIA RTX A4000 16 GB; Formato=Torre; Sistema=Windows 11 Pro',
        'Lenovo | ThinkStation P3 Tower | 7999 |  | Procesador=Intel Core i7-13700; RAM=32 GB DDR5; Almacenamiento=1 TB SSD; GPU=NVIDIA T1000 8 GB; Formato=Torre; Sistema=Windows 11 Pro',
    ],
    'equipos-pos': [
        'Sunmi | T2s POS 15.6" | 3299 |  | Pantalla=15.6" táctil + 10.1" cliente; Procesador=Qualcomm octa núcleo; Sistema=Android; Conectividad=Wi-Fi, Ethernet, Bluetooth; Impresora=Térmica 80 mm integrada',
        'Epson | TM-m30III | 1199 |  | Tipo=Impresora POS; Ancho=80 mm; Velocidad=Hasta 300 mm/s; Conectividad=USB, Ethernet, Wi-Fi; Corte=Automático',
    ],
    'lectores-codigos': [
        'Zebra | DS2208 | 499 |  | Tipo=Lector 1D/2D de mano; Lectura=Códigos de barras y QR, también en pantalla; Conectividad=USB; Resistencia=Caídas de 1.5 m',
        'Honeywell | Voyager 1472g inalámbrico | 799 |  | Tipo=Lector 1D/2D inalámbrico; Alcance=Hasta 10 m de la base; Conectividad=Bluetooth, USB; Batería=Hasta 14 h',
    ],
    'impresoras-termicas': [
        'Zebra | ZD421 | 1999 |  | Tipo=Impresora térmica de etiquetas; Ancho=Hasta 104 mm; Resolución=203 dpi; Conectividad=USB, Ethernet; Velocidad=Hasta 152 mm/s',
        'Xprinter | XP-58IIH | 159 |  | Tipo=Impresora térmica de boletas; Ancho=58 mm; Velocidad=Hasta 90 mm/s; Conectividad=USB',
        'Epson | TM-T20III | 899 |  | Tipo=Impresora térmica de boletas; Ancho=80 mm; Velocidad=Hasta 250 mm/s; Conectividad=USB, Ethernet; Corte=Automático',
    ],
    'centro-datos': [
        'APC | NetShelter SX 42U | 7499 |  | Tipo=Gabinete rack de piso; Altura=42U; Profundidad=1070 mm; Carga=Hasta 1361 kg',
        'Tripp Lite | PDU monitorizada 1U 8 tomas | 1499 |  | Tipo=PDU monitorizada; Tomas=8x IEC C13; Montaje=Rack 1U; Gestión=Pantalla de consumo',
        'StarTech | Organizador de cables horizontal 1U | 99 |  | Tipo=Panel organizador; Montaje=Rack 1U; Material=Acero',
    ],
    # ---------------------------------------------------------------- Robótica y electrónica
    'arduino': [
        'Arduino | Uno R4 WiFi | 149 |  | Microcontrolador=Renesas RA4M1 (Arm Cortex-M4); Conectividad=Wi-Fi, Bluetooth (ESP32-S3); E/S=14 digitales, 6 analógicas; Extra=Matriz LED 12x8',
        'Arduino | Nano Every | 69 |  | Microcontrolador=ATmega4809; E/S=14 digitales, 8 analógicas; Alimentación=5 V; Formato=Nano',
        'Arduino | Mega 2560 Rev3 | 179 |  | Microcontrolador=ATmega2560; E/S=54 digitales, 16 analógicas; Memoria=256 KB flash; Alimentación=7 a 12 V',
        'Arduino | Starter Kit en español | 449 |  | Incluye=Arduino Uno, 15 proyectos guiados y componentes; Nivel=Principiante; Libro=Manual impreso',
    ],
    'raspberry-pi': [
        'Raspberry Pi | 5 8GB | 449 |  | Procesador=Broadcom BCM2712 Arm Cortex-A76 2.4 GHz; RAM=8 GB LPDDR4X; Conectividad=Wi-Fi 5, Bluetooth 5.0, Ethernet Gigabit; Video=2x micro-HDMI 4K60',
        'Raspberry Pi | 4 Model B 4GB | 299 |  | Procesador=Broadcom BCM2711 Arm Cortex-A72 1.8 GHz; RAM=4 GB LPDDR4; Conectividad=Wi-Fi 5, Bluetooth 5.0, Ethernet Gigabit; Video=2x micro-HDMI 4K',
        'Raspberry Pi | Zero 2 W | 89 |  | Procesador=Arm Cortex-A53 cuádruple 1 GHz; RAM=512 MB; Conectividad=Wi-Fi 4, Bluetooth 4.2; Video=Mini-HDMI',
        'Raspberry Pi | Fuente oficial USB-C 27 W | 59 |  | Potencia=27 W; Salida=5.1 V 5 A; Conector=USB-C; Compatibilidad=Raspberry Pi 5',
    ],
    'esp32': [
        'Espressif | ESP32-DevKitC V4 | 49 |  | Microcontrolador=ESP32-WROOM-32E doble núcleo; Conectividad=Wi-Fi 4, Bluetooth 4.2; Memoria=4 MB flash; E/S=34 GPIO',
        'Espressif | ESP32-S3-DevKitC-1 | 69 |  | Microcontrolador=ESP32-S3 doble núcleo; Conectividad=Wi-Fi 4, Bluetooth 5 LE; Memoria=8 MB flash, 8 MB PSRAM; E/S=45 GPIO',
        'Seeed Studio | XIAO ESP32C3 | 35 |  | Microcontrolador=ESP32-C3 RISC-V; Conectividad=Wi-Fi 4, Bluetooth 5 LE; Formato=Ultra compacto 21 x 17.5 mm',
    ],
    'sensores-electronicos': [
        'HexaOrders | Kit de 37 sensores para Arduino | 129 |  | Incluye=37 módulos (temperatura, luz, sonido, movimiento, etc.); Compatibilidad=Arduino, ESP32, Raspberry Pi; Alimentación=3.3 a 5 V',
        'Bosch | BME280 módulo de temperatura, humedad y presión | 39 |  | Medición=Temperatura, humedad y presión; Interfaz=I2C, SPI; Alimentación=3.3 V',
        'HexaOrders | Sensor ultrasónico HC-SR04 (pack de 3) | 25 |  | Medición=Distancia de 2 a 400 cm; Interfaz=Digital; Alimentación=5 V; Unidades=3',
    ],
    'microcontroladores': [
        'Raspberry Pi | Pico 2 W | 39 |  | Microcontrolador=RP2350 Arm Cortex-M33 doble núcleo; Conectividad=Wi-Fi 4, Bluetooth 5.2; Memoria=4 MB flash; E/S=26 GPIO',
        'STMicroelectronics | Nucleo-F401RE | 79 |  | Microcontrolador=STM32F401 Arm Cortex-M4; Memoria=512 KB flash; Compatibilidad=Arduino Uno V3; Programación=ST-LINK integrado',
    ],
    'modulos-comunicacion': [
        'HexaOrders | Módulo LoRa SX1278 433 MHz | 45 |  | Tecnología=LoRa; Frecuencia=433 MHz; Alcance=Hasta 5 km en campo abierto; Interfaz=SPI',
        'SIMCom | Módulo SIM7600 4G LTE | 199 |  | Tecnología=4G LTE Cat-1; Funciones=Datos, SMS y GPS; Interfaz=UART, USB; Alimentación=5 V',
        'HexaOrders | Módulo Bluetooth HC-05 | 29 |  | Tecnología=Bluetooth 2.0 SPP; Alcance=Hasta 10 m; Interfaz=UART; Alimentación=3.3 a 5 V',
    ],
    'kits-robotica': [
        'Makeblock | mBot Neo | 699 |  | Edad=8 años o más; Programación=Bloques y Python; Conectividad=Wi-Fi, Bluetooth; Sensores=Ultrasonido, seguidor de línea y color',
        'LEGO | Education SPIKE Prime | 1999 |  | Edad=10 años o más; Programación=Bloques y Python; Piezas=528; Incluye=Hub, 3 motores y sensores',
        'HexaOrders | Kit chasis robot 4WD para Arduino | 149 |  | Motores=4 motores DC con reductora; Incluye=Chasis acrílico, driver L298N y portapilas; Compatibilidad=Arduino, ESP32',
    ],
    'motores': [
        'HexaOrders | Motor paso a paso NEMA 17 | 59 |  | Tipo=Paso a paso bipolar; Torque=0.45 N·m; Ángulo=1.8°; Uso=Impresoras 3D y CNC',
        'Pololu | Motorreductor 25D 12 V 100:1 con encoder | 149 |  | Tipo=DC con reductora; Velocidad=100 rpm; Encoder=Integrado; Alimentación=12 V',
    ],
    'servomotores': [
        'TowerPro | SG90 micro servo (pack de 5) | 35 |  | Torque=1.8 kg·cm; Rotación=180°; Engranajes=Plástico; Alimentación=4.8 a 6 V; Unidades=5',
        'TowerPro | MG996R servo metálico | 39 |  | Torque=11 kg·cm; Rotación=180°; Engranajes=Metálicos; Alimentación=4.8 a 7.2 V',
        'Dynamixel | XL430-W250-T | 349 |  | Torque=1.5 N·m; Rotación=360° multivuelta; Comunicación=TTL serie; Retroalimentación=Posición, velocidad y temperatura',
    ],
    'componentes-electronicos': [
        'HexaOrders | Kit de 1000 resistencias 1/4 W | 39 |  | Contenido=50 valores de 10 Ω a 1 MΩ; Tolerancia=1%; Potencia=1/4 W',
        'HexaOrders | Protoboard 830 puntos + 65 cables | 29 |  | Puntos=830; Incluye=65 cables jumper; Compatibilidad=Componentes de 2.54 mm',
        'HexaOrders | Kit de 500 condensadores cerámicos y electrolíticos | 49 |  | Contenido=24 valores de 10 pF a 1000 µF; Tipos=Cerámicos y electrolíticos',
    ],
    'instrumentos-medicion': [
        'Fluke | 117 Multímetro digital True RMS | 1299 |  | Tipo=Multímetro True RMS; Medición=Voltaje, corriente, resistencia, continuidad; Seguridad=CAT III 600 V; Función=VoltAlert sin contacto',
        'UNI-T | UT61E+ | 299 |  | Tipo=Multímetro True RMS; Medición=Voltaje, corriente, capacitancia y frecuencia; Seguridad=CAT III 600 V; Conectividad=USB',
        'Rigol | DS1054Z osciloscopio 4 canales | 2199 |  | Tipo=Osciloscopio digital; Ancho de banda=50 MHz; Canales=4; Muestreo=1 GSa/s; Pantalla=7"',
    ],
    # ---------------------------------------------------------------- Impresión 3D
    'impresoras-3d': [
        'Bambu Lab | A1 mini | 1499 |  | Tecnología=FDM; Volumen=180 x 180 x 180 mm; Velocidad=Hasta 500 mm/s; Calibración=Automática; Conectividad=Wi-Fi',
        'Bambu Lab | P1S | 3499 | 3799 | Tecnología=FDM cerrada; Volumen=256 x 256 x 256 mm; Velocidad=Hasta 500 mm/s; Calibración=Automática; Conectividad=Wi-Fi',
        'Creality | Ender-3 V3 SE | 899 |  | Tecnología=FDM; Volumen=220 x 220 x 250 mm; Velocidad=Hasta 250 mm/s; Calibración=Automática CR Touch; Conectividad=USB, microSD',
        'Elegoo | Saturn 4 Ultra | 2499 |  | Tecnología=Resina MSLA 12K; Volumen=218 x 123 x 220 mm; Resolución=19 x 24 µm; Conectividad=Wi-Fi, USB',
    ],
    'filamentos': [
        'Bambu Lab | PLA Basic 1 kg | 99 |  | Material=PLA; Diámetro=1.75 mm; Peso=1 kg; Temperatura=190 a 230 °C',
        'eSun | PETG 1 kg | 89 |  | Material=PETG; Diámetro=1.75 mm; Peso=1 kg; Temperatura=230 a 250 °C',
        'Polymaker | PolyTerra PLA 1 kg | 109 |  | Material=PLA mate; Diámetro=1.75 mm; Peso=1 kg; Temperatura=190 a 230 °C',
    ],
    'resinas': [
        'Elegoo | Resina estándar 8K 1 kg | 129 |  | Tipo=Fotopolímero estándar; Longitud de onda=405 nm; Peso=1 kg; Uso=Miniaturas y prototipos',
        'Anycubic | Resina lavable con agua 1 kg | 139 |  | Tipo=Fotopolímero lavable con agua; Longitud de onda=405 nm; Peso=1 kg; Uso=Impresión sin alcohol isopropílico',
    ],
    'repuestos-3d': [
        'Bambu Lab | Hotend completo 0.4 mm | 149 |  | Compatibilidad=Bambu Lab P1 y X1; Boquilla=Acero endurecido 0.4 mm; Instalación=Sin herramientas',
        'Creality | Cama PEI magnética 235 x 235 mm | 99 |  | Compatibilidad=Ender-3 y similares; Superficie=PEI texturizada; Base=Magnética flexible',
        'HexaOrders | Kit de 10 boquillas MK8 | 29 |  | Material=Latón; Diámetros=0.2 a 1.0 mm; Compatibilidad=Ender-3, CR-10',
    ],
    'escaneres-3d': [
        'Creality | CR-Scan Ferret Pro | 1499 |  | Precisión=Hasta 0.1 mm; Velocidad=Hasta 30 fps; Conectividad=USB, Wi-Fi; Uso=Objetos de 15 mm a 2000 mm',
        'Revopoint | POP 3 | 2299 |  | Precisión=Hasta 0.05 mm; Velocidad=Hasta 18 fps; Conectividad=USB, Wi-Fi; Uso=Objetos pequeños y medianos',
    ],
    'fabricacion-digital': [
        'xTool | D1 Pro 10 W | 2999 |  | Tipo=Grabadora y cortadora láser de diodo; Potencia=10 W; Área=430 x 390 mm; Conectividad=Wi-Fi, USB',
        'Cricut | Explore 3 | 1499 |  | Tipo=Plotter de corte; Ancho=Hasta 30 cm; Materiales=Vinilo, cartulina, tela y más; Conectividad=Bluetooth, USB',
    ],
    # ---------------------------------------------------------------- Wearables
    'smartwatches': [
        'Apple | Watch Series 10 GPS 42 mm | 1999 |  | Pantalla=OLED LTPO3; Sensores=ECG, oxígeno en sangre, temperatura; Resistencia=50 m; Batería=Hasta 18 h; Conectividad=Wi-Fi, Bluetooth 5.3, NFC',
        'Samsung | Galaxy Watch7 44 mm | 1299 | 1499 | Pantalla=Super AMOLED; Sensores=BioActive (ritmo cardíaco, ECG, composición corporal); Resistencia=5 ATM; Batería=Hasta 40 h; Conectividad=Wi-Fi, Bluetooth 5.3, NFC',
        'Google | Pixel Watch 3 41 mm | 1599 |  | Pantalla=AMOLED Actua; Sensores=ECG, Fitbit; Resistencia=5 ATM; Batería=Hasta 24 h; Conectividad=Wi-Fi, Bluetooth 5.3, NFC',
        'Huawei | Watch GT 5 46 mm | 999 |  | Pantalla=AMOLED 1.43"; Sensores=TruSense; Resistencia=5 ATM; Batería=Hasta 14 días; Conectividad=Bluetooth 5.2, NFC',
        'Apple | Watch Ultra 2 | 3999 |  | Pantalla=OLED LTPO2 de 3000 nits; Sensores=ECG, oxígeno, profundidad; Resistencia=100 m; Batería=Hasta 36 h; Conectividad=Wi-Fi, Bluetooth 5.3, NFC',
    ],
    'smartbands': [
        'Xiaomi | Smart Band 9 | 179 |  | Pantalla=AMOLED 1.62"; Sensores=Ritmo cardíaco, SpO2, sueño; Resistencia=5 ATM; Batería=Hasta 21 días; Conectividad=Bluetooth 5.4',
        'Huawei | Band 9 | 199 |  | Pantalla=AMOLED 1.47"; Sensores=TruSleep, SpO2; Resistencia=5 ATM; Batería=Hasta 14 días; Conectividad=Bluetooth 5.0',
        'Fitbit | Charge 6 | 699 |  | Pantalla=AMOLED; Sensores=ECG, GPS integrado; Resistencia=5 ATM; Batería=Hasta 7 días; Conectividad=Bluetooth, NFC',
    ],
    'relojes-deportivos': [
        'Garmin | Forerunner 265 | 1999 |  | Pantalla=AMOLED 1.3"; GPS=Multibanda; Resistencia=5 ATM; Batería=Hasta 13 días; Funciones=Preparación para entrenar',
        'Garmin | Fenix 8 47 mm AMOLED | 4499 |  | Pantalla=AMOLED 1.4"; GPS=Multibanda con mapas; Resistencia=10 ATM; Batería=Hasta 16 días; Funciones=Linterna LED y altavoz',
        'Coros | Pace 3 | 999 |  | Pantalla=MIP 1.2"; GPS=Doble frecuencia; Resistencia=5 ATM; Batería=Hasta 24 días; Peso=30 g',
        'Amazfit | T-Rex 3 | 1199 |  | Pantalla=AMOLED 1.5"; GPS=Doble banda; Resistencia=10 ATM; Batería=Hasta 27 días; Funciones=Mapas sin conexión',
    ],
    'anillos-inteligentes': [
        'Samsung | Galaxy Ring | 1699 |  | Sensores=Ritmo cardíaco, temperatura, SpO2; Resistencia=10 ATM; Batería=Hasta 7 días; Conectividad=Bluetooth 5.4',
        'Oura | Ring 4 | 1599 |  | Sensores=Ritmo cardíaco, temperatura, SpO2; Resistencia=100 m; Batería=Hasta 8 días; Conectividad=Bluetooth',
        'Amazfit | Helio Ring | 999 |  | Sensores=Ritmo cardíaco, temperatura, estrés; Resistencia=10 ATM; Batería=Hasta 4 días; Conectividad=Bluetooth 5.2',
    ],
    'accesorios-wearable': [
        'Apple | Correa Sport Loop 45 mm | 199 |  | Compatibilidad=Apple Watch 42/44/45/46 mm; Material=Nailon tejido; Cierre=Gancho y bucle',
        'Spigen | Protector de pantalla Apple Watch (pack de 3) | 59 |  | Compatibilidad=Apple Watch 45 mm; Material=Vidrio templado; Unidades=3',
        'Garmin | Cable de carga USB-C | 79 |  | Compatibilidad=Relojes Garmin con conector de 4 pines; Conector=USB-C; Longitud=1 m',
    ],
    # ---------------------------------------------------------------- Realidad virtual y aumentada
    'visores-vr': [
        'Meta | Quest 3 512GB | 2999 | 3299 | Resolución=2064x2208 por ojo; Frecuencia=120 Hz; Procesador=Qualcomm Snapdragon XR2 Gen 2; Almacenamiento=512 GB; Seguimiento=Inside-out con color passthrough',
        'Meta | Quest 3S 128GB | 1599 |  | Resolución=1832x1920 por ojo; Frecuencia=120 Hz; Procesador=Qualcomm Snapdragon XR2 Gen 2; Almacenamiento=128 GB; Seguimiento=Inside-out',
        'Sony | PlayStation VR2 | 2299 |  | Resolución=2000x2040 por ojo OLED; Frecuencia=120 Hz; Compatibilidad=PS5 y PC con adaptador; Seguimiento=Ocular e inside-out',
    ],
    'accesorios-vr': [
        'Meta | Correa Elite con batería para Quest 3 | 549 |  | Compatibilidad=Meta Quest 3 y 3S; Batería=Duplica la autonomía; Ajuste=Rueda de ajuste',
        'Kiwi Design | Estación de carga para Quest 3 | 249 |  | Compatibilidad=Meta Quest 3 y controles; Carga=Visor y 2 controles; Conectividad=USB-C magnético',
    ],
    'controladores-vr': [
        'Meta | Controles Touch Plus (par) | 699 |  | Compatibilidad=Meta Quest 3 y 3S; Seguimiento=Sin anillo de seguimiento; Batería=AA (incluidas); Háptica=TruTouch',
        'Valve | Index Controllers (par) | 1299 |  | Compatibilidad=SteamVR con estaciones base; Seguimiento=De dedos individuales; Batería=Hasta 7 h',
    ],
    'realidad-mixta': [
        'XREAL | Air 2 Pro | 1999 |  | Tipo=Gafas de realidad aumentada; Pantalla=Micro-OLED 1080p por ojo; Frecuencia=120 Hz; Conectividad=USB-C DisplayPort',
        'Rokid | Max AR Glasses | 2199 |  | Tipo=Gafas de realidad aumentada; Pantalla=Micro-OLED 1080p; Campo de visión=50°; Conectividad=USB-C',
    ],
    # ---------------------------------------------------------------- Energía y protección eléctrica
    'ups': [
        'APC | Back-UPS BX1200MI-MS 1200 VA | 699 |  | Capacidad=1200 VA / 650 W; Tipo=Línea interactiva; Tomas=4 universales; Autonomía=Hasta 20 min (carga típica); Conectividad=USB',
        'Forza | NT-1012U 1000 VA | 399 |  | Capacidad=1000 VA / 500 W; Tipo=Línea interactiva; Tomas=8 (4 con respaldo); Autonomía=Hasta 15 min; Conectividad=USB',
        'CyberPower | CP1500PFCLCD 1500 VA | 1499 |  | Capacidad=1500 VA / 1000 W; Tipo=Línea interactiva onda sinusoidal pura; Tomas=10; Pantalla=LCD; Conectividad=USB',
    ],
    'estabilizadores-tension': [
        'Forza | FVR-1202 1200 VA | 129 |  | Capacidad=1200 VA / 600 W; Tomas=8; Protección=Sobretensión y cortocircuito; Rango=90 a 145 V',
        'CDP | R-2C AVR 1200 VA | 149 |  | Capacidad=1200 VA / 600 W; Tomas=4; Protección=Sobretensión y picos; Tipo=Regulador electrónico',
    ],
    'supresores': [
        'APC | SurgeArrest P8VT3 | 249 |  | Tomas=8; Protección=2525 J; Extra=Protección coaxial y telefónica; Cable=3 m',
        'Belkin | Surge Protector 12 tomas con USB | 199 |  | Tomas=12; Protección=3940 J; Extra=2 puertos USB; Cable=2.4 m',
    ],
    'cargadores-gan': [
        'Anker | 735 Charger GaNPrime 65 W | 199 | 229 | Potencia=65 W; Puertos=2x USB-C, 1x USB-A; Tecnología=GaN; Compatibilidad=Laptops, tablets y smartphones',
        'Ugreen | Nexode 100 W GaN 4 puertos | 299 |  | Potencia=100 W; Puertos=3x USB-C, 1x USB-A; Tecnología=GaN; Compatibilidad=Laptops, tablets y smartphones',
        'Baseus | GaN5 Pro 30 W | 89 |  | Potencia=30 W; Puertos=1x USB-C, 1x USB-A; Tecnología=GaN; Compatibilidad=Smartphones y tablets',
    ],
    'estaciones-energia': [
        'EcoFlow | River 2 Pro | 2299 |  | Capacidad=768 Wh; Salida=800 W (pico 1600 W); Tomas=4 AC, 3 USB-A, 2 USB-C; Carga=0 a 100% en 70 min',
        'Jackery | Explorer 300 Plus | 1299 |  | Capacidad=288 Wh; Salida=300 W; Tomas=1 AC, 1 USB-A, 2 USB-C; Peso=3.75 kg',
        'Anker | SOLIX C1000 | 3299 |  | Capacidad=1056 Wh; Salida=1800 W; Tomas=6 AC, 2 USB-A, 2 USB-C; Carga=0 a 100% en 58 min',
    ],
    'adaptadores-electricos': [
        'Ugreen | Adaptador universal de viaje con USB-C 20 W | 99 |  | Compatibilidad=Más de 200 países; Puertos=2x USB-A, 1x USB-C; Potencia=20 W',
        'HexaOrders | Adaptador de enchufe tipo A/C | 19 |  | Tipo=Adaptador de clavija plana a redonda; Corriente=Hasta 10 A; Voltaje=Hasta 250 V',
    ],
    'regletas-inteligentes': [
        'TP-Link | Tapo P300 regleta Wi-Fi | 149 |  | Tomas=3 controlables + 2 USB-A + 1 USB-C; Conectividad=Wi-Fi 2.4 GHz; Protección=Sobretensión; Compatibilidad=Alexa, Google Home',
        'Meross | Regleta inteligente Matter 4 tomas | 199 |  | Tomas=4 controlables + 4 USB; Conectividad=Wi-Fi, Matter; Compatibilidad=Apple Home, Google Home, Alexa',
    ],
    # ---------------------------------------------------------------- Tecnología para productividad
    'videoconferencia': [
        'Logitech | MeetUp | 3999 |  | Tipo=Cámara conferencia todo en uno; Video=4K Ultra HD con zoom 5x; Audio=3 micrófonos y altavoz; Conectividad=USB, Bluetooth; Sala=Pequeña (hasta 6 personas)',
        'Poly | Studio P5 con altavoz | 899 |  | Tipo=Webcam profesional 1080p; Audio=Altavoz manos libres incluido; Conectividad=USB; Compatibilidad=Teams, Zoom',
        'Jabra | PanaCast 20 | 1199 |  | Tipo=Webcam inteligente 4K; Audio=3 micrófonos; Conectividad=USB-C; Función=Encuadre automático con IA',
    ],
    'pizarras-digitales': [
        'Samsung | Flip Pro 55" WM55B | 9999 |  | Pantalla=55" táctil UHD; Escritura=Hasta 20 puntos simultáneos; Conectividad=Wi-Fi, USB-C, HDMI; Uso=Reuniones y clases',
        'Maxhub | V6 Classic 65" | 8999 |  | Pantalla=65" táctil 4K; Escritura=Hasta 20 puntos; Sistema=Android integrado; Conectividad=Wi-Fi, HDMI, USB-C',
    ],
    'presentadores': [
        'Logitech | R500s | 149 |  | Alcance=Hasta 20 m; Conectividad=Bluetooth, receptor USB; Puntero=Láser rojo; Batería=1x AAA',
        'Logitech | Spotlight | 449 |  | Alcance=Hasta 30 m; Conectividad=Bluetooth, receptor USB; Puntero=Digital con resaltado; Batería=Recargable hasta 3 meses',
    ],
    'colaboracion': [
        'Barco | ClickShare CX-20 Gen2 | 3999 |  | Tipo=Presentación inalámbrica; Usuarios=Hasta 8; Conectividad=Wi-Fi, USB-C; Compatibilidad=Teams, Zoom',
        'Logitech | Tap IP | 2999 |  | Tipo=Controlador táctil para salas; Pantalla=10.1" táctil; Conectividad=PoE; Compatibilidad=Teams, Zoom, Google Meet',
    ],
    'accesorios-ergonomicos': [
        'Logitech | Reposamuñecas Mouse Pad Studio | 89 |  | Tipo=Mousepad con reposamuñecas; Material=Espuma viscoelástica; Base=Antideslizante',
        'HexaOrders | Soporte elevador para laptop ajustable | 129 |  | Compatibilidad=Laptops de 10" a 17"; Material=Aluminio; Ajustes=6 alturas; Peso=0.9 kg',
        'Fellowes | Reposapiés ergonómico ajustable | 199 |  | Tipo=Reposapiés con inclinación; Ajustes=Altura e inclinación; Material=Plástico con superficie texturizada',
    ],
})

# Productos existentes antes de la v6 (V3): subcategoría nueva, marca, modelo y ficha técnica.
# BIE-BOTTLE no es un producto tecnológico: se conserva (pedidos e historial) pero deja de venderse.
LEGACY = {
    'TEC-LAPTOP-14': ('ultrabooks', 'HexaOrders', 'Hexa 14 (2024)', 'Procesador=Intel Core i7-1360P; RAM=16 GB LPDDR5; Almacenamiento=512 GB SSD; Pantalla=14" IPS; Resolución=1920x1200; Peso=1.20 kg; Sistema=Windows 11 Home'),
    'TEC-MONITOR-27': ('monitores-qhd', 'HexaOrders', 'View 27Q', 'Pantalla=27" IPS; Resolución=2560x1440; Frecuencia=144 Hz; Respuesta=1 ms; Conexiones=HDMI, DisplayPort'),
    'TEC-TABLET-11': ('tablets-android', 'HexaOrders', 'Tab 11 Pro', 'Pantalla=11" IPS; Frecuencia=120 Hz; Almacenamiento=256 GB; Incluye=Lápiz; Conectividad=Wi-Fi 6, Bluetooth 5.2'),
    'TEC-DOCK-USBC': ('docking-stations', 'HexaOrders', 'Dock 9-en-1', 'Conexión=USB-C; Carga=Hasta 100 W; Video=HDMI 4K; Puertos=Ethernet, lector SD, 3x USB-A'),
    'AUD-HEADSET-NC': ('audifonos-bluetooth', 'HexaOrders', 'Sound NC1', 'Tipo=Over-ear; Cancelación de ruido=Activa; Conectividad=Bluetooth 5.3; Batería=Hasta 40 h'),
    'AUD-SPEAKER-MINI': ('parlantes', 'HexaOrders', 'Mini Go', 'Potencia=10 W; Conectividad=Bluetooth 5.0; Batería=Hasta 12 h; Resistencia=IP67'),
    'AUD-MIC-USB': ('microfonos-usb', 'HexaOrders', 'Studio USB', 'Tipo=Condensador; Patrón=Cardioide; Resolución=24 bits / 48 kHz; Conectividad=USB'),
    'OFI-CHAIR-ERGO': ('accesorios-ergonomicos', 'HexaOrders', 'Ergo Mesh', 'Tipo=Silla ergonómica para estación de trabajo; Soporte lumbar=Ajustable; Material=Malla transpirable'),
    'OFI-DESK-LIFT': ('accesorios-ergonomicos', 'HexaOrders', 'Lift Duo 140', 'Tipo=Escritorio eléctrico de altura regulable; Motor=Doble; Memorias=4 alturas; Superficie=140 x 70 cm'),
    'OFI-LAMP-LED': ('accesorios-ergonomicos', 'HexaOrders', 'Desk LED Q', 'Tipo=Lámpara LED de escritorio; Temperatura=Regulable; Carga inalámbrica=Qi 10 W'),
    'HOG-CAM-WIFI': ('camaras-inteligentes', 'HexaOrders', 'Cam 2K', 'Resolución=2560x1440; Visión nocturna=Infrarroja; Detección=Personas; Conectividad=Wi-Fi 2.4 GHz'),
    'HOG-PLUG-SMART': ('enchufes-inteligentes', 'HexaOrders', 'Plug Duo', 'Unidades=2; Función=Medición de consumo y programación; Conectividad=Wi-Fi 2.4 GHz'),
    'HOG-HUB': ('controladores-smart', 'HexaOrders', 'Home Hub', 'Protocolos=Zigbee, Thread; Conectividad=Wi-Fi, Ethernet, Matter'),
    'ACC-BACKPACK': ('accesorios-laptop', 'HexaOrders', 'Tech Pack 20', 'Compatibilidad=Laptops de hasta 16"; Capacidad=20 L; Material=Poliéster repelente al agua'),
    'ACC-CHARGER-65': ('cargadores-gan', 'HexaOrders', 'GaN 65', 'Potencia=65 W; Puertos=2x USB-C, 1x USB-A; Tecnología=GaN; Carga rápida=Power Delivery 3.0'),
    'ACC-MOUSE-PRO': ('mouse', 'HexaOrders', 'Mouse Pro', 'Sensor=Óptico 8000 DPI; DPI=Hasta 8000; Conectividad=Bluetooth, 2.4 GHz; Multidispositivo=3 equipos'),
    'BIE-WATCH-FIT': ('relojes-deportivos', 'HexaOrders', 'Fit GPS', 'GPS=Integrado; Sensores=Frecuencia cardíaca; Batería=Hasta 10 días; Resistencia=5 ATM'),
}
LEGACY_RETIRED = ['BIE-BOTTLE']
LEGACY_HIDDEN_CATEGORIES = ['tecnologia', 'audio', 'oficina', 'hogar', 'accesorios', 'bienestar']

# Campañas comerciales: slug, nombre, descripción, % de descuento, días desde la carga hasta el inicio
# (negativo = ya empezó), duración en días y categorías principales o subcategorías objetivo.
PROMOTIONS = [
    ('semana-gaming', 'Semana Gaming', 'Consolas, periféricos y accesorios gaming con descuento.', 15, -2, 120, ['gaming']),
    ('ofertas-componentes', 'Ofertas en componentes', 'Arma o mejora tu PC: procesadores, gráficas, memorias y más.', 10, -5, 150, ['componentes']),
    ('descuentos-laptops', 'Descuentos en laptops', 'Laptops de oficina, gaming y ultrabooks a precio especial.', 8, -3, 120, ['laptops-oficina', 'laptops-gaming', 'ultrabooks']),
    ('promo-smartphones', 'Promociones de smartphones', 'Smartphones Android y iPhone con descuento.', 7, -1, 90, ['smartphones-android', 'iphone']),
    ('ofertas-monitores', 'Ofertas de monitores', 'Monitores Full HD, QHD, 4K y gaming.', 12, -4, 120, ['monitores']),
    ('campana-perifericos', 'Campaña de periféricos', 'Teclados, mouse, webcams y docking stations.', 10, -2, 90, ['perifericos']),
    ('descuentos-almacenamiento', 'Descuentos en almacenamiento', 'SSD externos, memorias, tarjetas y NAS.', 15, -6, 150, ['almacenamiento', 'ssd-nvme', 'ssd-sata']),
    ('hogar-inteligente', 'Tecnología inteligente para tu hogar', 'Focos, enchufes, cámaras y asistentes inteligentes.', 20, -1, 90, ['smart-home']),
    ('cyber-wearables', 'Cyber Wearables', 'Smartwatches y smartbands con descuento especial.', 18, 20, 30, ['wearables']),
]
