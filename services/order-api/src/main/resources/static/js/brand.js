// POLYMER — configuración de marca en un solo lugar (nombre, eslogan, contacto, redes, datos legales,
// intro). La interfaz lee estos valores; para una puesta en producción basta con editar este módulo.
//
// ⚠ DATOS DE DEMOSTRACIÓN: los teléfonos, correos (dominio reservado example.com), dirección y datos
// legales de abajo NO son reales. No hay RUC ni razón social inscritos, y las redes sociales no tienen
// cuentas oficiales: mientras su URL sea null, el icono lleva a la página que lo explica.

export const BRAND = Object.freeze({
  name: 'POLYMER',
  displayName: 'Polymer',
  slogan: 'Tecnología que conecta contigo.',
  description: 'Tienda de tecnología: laptops, componentes, gaming, smartphones, redes y smart home, con asesoría de especialistas y envíos a todo el Perú.',
  logo: '/img/polymer-logo.svg',
  logoLight: '/img/polymer-logo-light.svg',
  mark: '/img/polymer-mark.svg',
});

export const CONTACT = Object.freeze({
  demo: true,                                  // DEMO: no son canales reales
  phone: '+51 900 000 000',
  whatsapp: '+51 900 000 000',
  supportEmail: 'soporte@example.com',
  corporateEmail: 'contacto@example.com',
  hours: 'Lunes a sábado, de 9:00 a. m. a 6:00 p. m.',
  city: 'Lima, Perú',
});

export const LEGAL = Object.freeze({
  demo: true,                                  // DEMO: completar con los datos reales al constituir la empresa
  companyName: null,                           // razón social: no configurada
  taxId: null,                                 // RUC: no configurado (no se inventa uno)
  address: 'Lima, Perú',
  note: 'Tienda de demostración: razón social y RUC no configurados.',
});

// URL null = sin cuenta oficial todavía (no se simula ninguna). Al crearla, pegar aquí su URL.
export const SOCIAL = Object.freeze([
  { id: 'facebook', label: 'Facebook', url: null },
  { id: 'instagram', label: 'Instagram', url: null },
  { id: 'tiktok', label: 'TikTok', url: null },
  { id: 'youtube', label: 'YouTube', url: null },
  { id: 'linkedin', label: 'LinkedIn', url: null },
  { id: 'whatsapp', label: 'WhatsApp', url: null },
]);
export const SOCIAL_FALLBACK = '/empresa.html#redes';

// Métodos de pago: solo los del checkout funcionan. Los demás se muestran como «no disponibles».
export const PAYMENTS = Object.freeze({
  available: ['Pago contra entrega', 'Transferencia bancaria', 'Pago en tienda'],
  notIntegrated: ['Tarjetas de crédito y débito', 'Billeteras digitales'],
});

// Intro cinematográfica: una vez por sesión del navegador. La duración no incluye la apertura del telón.
export const INTRO = Object.freeze({
  enabled: true,
  durationMs: 3400,
  storageKey: 'polymer.intro.seen',
});

// Geometría del isotipo (la misma que tools/generate_brand.py) para animarlo trazo a trazo.
export const MARK_GEOMETRY = Object.freeze({
  lines: ['M18 50 V14', 'M18 14 H37 L47 24 V29 L37 39 H27', 'M27 22 H34 L38 26 V27 L34 31 H27'],
  signal: 'M51 26.5 H56',
  nodes: [[18, 51, 3.8], [27, 39, 3.3], [27, 22, 3.3]],
  core: [27, 31, 2.1],
});
