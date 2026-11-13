// Estado de sesión del usuario. La autorización real ocurre en el backend: aquí solo se decide
// qué navegación mostrar. Un botón oculto no protege nada; el servidor responde 401/403.

import { authApi, forgetCsrf, refreshCsrf } from './api.js';
import { t } from './i18n.js';

let current = null; // { id, username, displayName, roles: [] } | null

export const ROLE_LABELS = {
  CUSTOMER: t('Cliente'),
  ADMIN: t('Administración'),
  ORDER_OPERATOR: t('Operador de pedidos'),
  LOGISTICS_OPERATOR: t('Operador logístico'),
  OBSERVABILITY_VIEWER: t('Observabilidad'),
};

/** Página de inicio de cada rol tras iniciar sesión (páginas HTML reales del sitio multipágina). */
export function homeFor(user) {
  if (!user) return '/catalogo.html';
  const roles = user.roles || [];
  if (roles.includes('ADMIN')) return '/admin/dashboard.html';
  if (roles.includes('ORDER_OPERATOR')) return '/operaciones/dashboard.html';
  if (roles.includes('LOGISTICS_OPERATOR')) return '/logistica/dashboard.html';
  if (roles.includes('OBSERVABILITY_VIEWER')) return '/monitoreo/dashboard.html';
  if (roles.includes('CUSTOMER')) return '/cliente/dashboard.html';
  return '/catalogo.html';
}

function emit() { window.dispatchEvent(new CustomEvent('polymer:session', { detail: current })); }

export const session = {
  user: () => current,
  isAuthenticated: () => current !== null,
  has: (...roles) => current !== null && roles.some((role) => current.roles.includes(role)),

  async load() {
    try {
      const me = await authApi.me();
      current = me.authenticated ? me.user : null;
    } catch {
      current = null;
    }
    emit();
    return current;
  },

  /**
   * v7: devuelve el usuario si la sesión quedó abierta, o { verificationRequired, challenge } si falta el
   * código de 6 dígitos enviado por correo (luego session.verify).
   */
  async login(username, password, { rememberMe = false, next = null } = {}) {
    await refreshCsrf();
    const result = await authApi.login(username, password, rememberMe, next);
    if (result.verificationRequired) return result;
    current = result.user;
    await refreshCsrf(); // el token puede cambiar con la nueva sesión
    emit();
    return current;
  },

  /** v7: segundo paso (código por correo). Devuelve { user, next }. */
  async verify(code, rememberMe) {
    const result = await authApi.verify(code, rememberMe);
    current = result.user;
    await refreshCsrf();
    emit();
    return result;
  },

  async logout() {
    try { await authApi.logout(); } finally {
      current = null;
      forgetCsrf();
      emit();
    }
  },

  /** Llamado cuando el servidor responde 401 (sesión caducada). */
  expire() {
    if (current) {
      current = null;
      forgetCsrf();
      emit();
    }
  },
};
