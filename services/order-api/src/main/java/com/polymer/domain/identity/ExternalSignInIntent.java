package com.polymer.domain.identity;

/**
 * v9 · Qué pidió la persona al pulsar el botón de Google.
 * SIGN_IN («Iniciar sesión con Google»): solo entra si ya existe una cuenta POLYMER (vinculada a esa cuenta
 * de Google o con el mismo correo verificado); si no, se rechaza y se le invita a registrarse.
 * SIGN_UP («Registrarse con Google»): crea la cuenta de cliente si no existe; si ya existe, inicia sesión.
 */
public enum ExternalSignInIntent { SIGN_IN, SIGN_UP }
