-- v9 · Testimonios del carrusel: el equipo de POLYMER (con su perfil de LinkedIn) y los clientes con cuenta.
-- Uno por cliente (se puede editar); la administración puede ocultarlos. No borra ni cambia datos existentes.
CREATE TABLE testimonials (
    id           BIGSERIAL PRIMARY KEY,
    kind         VARCHAR(10)  NOT NULL CHECK (kind IN ('TEAM', 'CUSTOMER')),
    user_id      BIGINT       UNIQUE REFERENCES app_users (id) ON DELETE CASCADE,
    name         VARCHAR(80)  NOT NULL,
    role         VARCHAR(80),
    photo_url    VARCHAR(200),
    linkedin_url VARCHAR(200) CHECK (linkedin_url IS NULL OR linkedin_url ~ '^https://(www\.)?linkedin\.com/in/[A-Za-z0-9%_-]{3,100}/?$'),
    comment      VARCHAR(600) NOT NULL CHECK (char_length(comment) >= 20),
    rating       SMALLINT     CHECK (rating BETWEEN 1 AND 5),
    visible      BOOLEAN      NOT NULL DEFAULT TRUE,
    position     INTEGER      NOT NULL DEFAULT 100,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT testimonials_customer_has_account CHECK (kind = 'TEAM' OR (user_id IS NOT NULL AND rating IS NOT NULL))
);
CREATE INDEX testimonials_visible_idx ON testimonials (visible, position, created_at DESC);

-- Equipo POLYMER. Los comentarios son los textos iniciales del equipo y se pueden editar con SQL o desde la
-- administración (ocultar/mostrar).
INSERT INTO testimonials (kind, name, role, photo_url, linkedin_url, comment, rating, position) VALUES
('TEAM', 'Ronaldo Gabriel Matos Martínez', 'Equipo de desarrollo POLYMER', '/img/equipo/ronaldo-matos.jpg',
 'https://www.linkedin.com/in/ronaldo-gabriel-matos-mart%C3%ADnez-614841392/',
 'Construir POLYMER sobre una base sólida y ordenada nos permitió sumar pagos en línea, acceso con Google y nuevas funciones sin tocar el corazón del negocio. Es una tienda rápida, segura y lista para crecer.', 5, 1),
('TEAM', 'Khyara Yassy Morales Fernández', 'Equipo de desarrollo POLYMER', '/img/equipo/khyara-morales.jpg',
 'https://www.linkedin.com/in/khyara-morales-053a75444/',
 'Cuidamos cada detalle de la experiencia: desde la búsqueda de un producto hasta la boleta que llega a tu correo. POLYMER demuestra que comprar tecnología en línea puede ser claro, bonito y confiable.', 5, 2),
('TEAM', 'Jonathan David Retuerto Gonzáles', 'Equipo de desarrollo POLYMER', '/img/equipo/jonathan-retuerto.jpg',
 'https://www.linkedin.com/in/jonathan-retuerto-5b089a395/',
 'Lo que más valoro de POLYMER es su solidez: cada pedido se evalúa, se guarda y se notifica aunque un servicio falle. Es una plataforma pensada para que el cliente compre con total tranquilidad.', 5, 3);
