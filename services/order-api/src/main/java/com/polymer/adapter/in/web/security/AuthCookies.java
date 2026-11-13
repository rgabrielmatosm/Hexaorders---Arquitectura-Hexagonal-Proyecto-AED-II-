package com.polymer.adapter.in.web.security;

import com.polymer.domain.identity.VerificationPolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;

/**
 * Cookies de «Recordarme durante 30 días». HttpOnly (JavaScript no las lee), SameSite=Lax y Secure
 * cuando la petición llegó por HTTPS. Su valor es «serie.ficha»; el servidor solo guarda el hash.
 */
public final class AuthCookies {
    /** Sesión persistente: vuelve a abrir la sesión al volver dentro de 30 días. */
    public static final String REMEMBER = "POLYMER_REMEMBER";
    /** Dispositivo de confianza: no se vuelve a pedir el código de verificación durante 30 días. */
    public static final String DEVICE = "POLYMER_DEVICE";

    private AuthCookies() {}

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, String name, String value) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .path("/").maxAge(VerificationPolicy.REMEMBER_FOR).build().toString());
    }

    public static void clear(HttpServletRequest request, HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, "").httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .path("/").maxAge(0).build().toString());
    }
}
