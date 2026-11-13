package com.polymer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * v8 · Credenciales de «Continuar con Google» leídas del JSON que descarga Google Cloud
 * (Google Auth Platform → Clientes → Aplicación web → Descargar JSON), montado como Docker secret
 * en /run/secrets/google_oauth desde secrets/google-oauth.json.
 *
 * Solo se usan web.client_id, web.client_secret y web.redirect_uris. Los mensajes de error nunca
 * incluyen valores del archivo: describen qué falta y cómo corregirlo.
 */
public record GoogleOAuthCredentials(String clientId, String clientSecret, List<String> redirectUris) {
    private static final Pattern CLIENT_ID = Pattern.compile("^[0-9]+-[A-Za-z0-9_]+\\.apps\\.googleusercontent\\.com$");
    public static final String CALLBACK_PATH = "/login/oauth2/code/google";

    /** Resultado de leer el archivo: credenciales, o un problema explicable sin datos sensibles. */
    public record Reading(GoogleOAuthCredentials credentials, String problem) {
        static Reading missing() { return new Reading(null, null); }
        static Reading problem(String text) { return new Reading(null, text); }
        public boolean present() { return credentials != null; }
    }

    /** Lee el archivo; vacío o inexistente = no configurado (sin problema). */
    public static Reading read(Path file) {
        if (file == null || !Files.isRegularFile(file)) return Reading.missing();
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            return Reading.problem("No se pudo leer secrets/google-oauth.json");
        }
        if (text.startsWith("﻿")) text = text.substring(1);
        if (text.isEmpty()) return Reading.missing();
        return parse(text);
    }

    static Reading parse(String text) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(text);
        } catch (IOException invalid) {
            return Reading.problem("secrets/google-oauth.json no es un JSON válido: descárgalo de nuevo desde Google Cloud");
        }
        if (root == null || !root.isObject()) return Reading.problem("secrets/google-oauth.json no es un JSON válido");
        JsonNode web = root.path("web");
        if (!web.isObject()) {
            return Reading.problem(root.has("installed")
                    ? "secrets/google-oauth.json es de una app de escritorio («installed»); crea un cliente de tipo «Aplicación web»"
                    : "secrets/google-oauth.json no tiene la sección «web» (cliente OAuth de tipo «Aplicación web»)");
        }
        String id = web.path("client_id").asText("").strip();
        String secret = web.path("client_secret").asText("").strip();
        if (!CLIENT_ID.matcher(id).matches()) return Reading.problem("secrets/google-oauth.json: web.client_id falta o no tiene el formato de Google");
        if (secret.isEmpty()) return Reading.problem("secrets/google-oauth.json: falta web.client_secret");
        List<String> uris = new ArrayList<>();
        web.path("redirect_uris").forEach(node -> uris.add(node.asText("").strip()));
        return new Reading(new GoogleOAuthCredentials(id, secret, List.copyOf(uris)), null);
    }

    /** URI de retorno que Google debe tener registrada para un origen público (p. ej. https://tienda.ejemplo.com). */
    public static String callbackFor(String publicUrl) {
        return publicUrl.strip().replaceAll("/+$", "") + CALLBACK_PATH;
    }

    public boolean allows(String redirectUri) { return redirectUris.contains(redirectUri); }

    /** Nunca muestra el secreto (los records lo incluirían en toString). */
    @Override
    public String toString() { return "GoogleOAuthCredentials[clientId=…" + clientId.substring(Math.max(0, clientId.indexOf('.') - 4)) + ", redirectUris=" + redirectUris.size() + "]"; }
}
