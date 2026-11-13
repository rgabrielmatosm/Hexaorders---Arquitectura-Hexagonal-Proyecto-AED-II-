package com.polymer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** v8 · JSON de credenciales OAuth de Google (valores FICTICIOS: nunca credenciales reales en las pruebas). */
class GoogleOAuthCredentialsTest {
    private static final String ID = "123456789012-ficticio.apps.googleusercontent.com";
    private static final String SECRET = "GOCSPX-solo-para-pruebas";
    private static final String WEB = """
            {"web":{"client_id":"%s","project_id":"demo","auth_uri":"https://accounts.google.com/o/oauth2/auth",
             "token_uri":"https://oauth2.googleapis.com/token","client_secret":"%s",
             "redirect_uris":["http://localhost:8080/login/oauth2/code/google","http://127.0.0.1:8080/login/oauth2/code/google"],
             "javascript_origins":["http://localhost:8080"]}}""".formatted(ID, SECRET);

    @TempDir Path dir;

    @Test
    void readsTheWebClientDownloadedFromGoogleCloud() throws Exception {
        Path file = Files.writeString(dir.resolve("google-oauth.json"), "﻿" + WEB, StandardCharsets.UTF_8);
        GoogleOAuthCredentials.Reading reading = GoogleOAuthCredentials.read(file);
        assertThat(reading.present()).isTrue();
        assertThat(reading.problem()).isNull();
        GoogleOAuthCredentials credentials = reading.credentials();
        assertThat(credentials.clientId()).isEqualTo(ID);
        assertThat(credentials.clientSecret()).isEqualTo(SECRET);
        assertThat(credentials.allows("http://localhost:8080/login/oauth2/code/google")).isTrue();
        assertThat(credentials.allows("http://127.0.0.1:8080/login/oauth2/code/google")).isTrue();
        assertThat(credentials.allows("https://tienda.ejemplo.com/login/oauth2/code/google")).isFalse();
        assertThat(credentials.toString()).doesNotContain(SECRET).doesNotContain("123456789012");
    }

    @Test
    void missingOrEmptyFileMeansNotConfiguredWithoutError() throws Exception {
        assertThat(GoogleOAuthCredentials.read(dir.resolve("no-existe.json")).present()).isFalse();
        assertThat(GoogleOAuthCredentials.read(dir.resolve("no-existe.json")).problem()).isNull();
        Path empty = Files.writeString(dir.resolve("vacio.json"), "  \n");
        assertThat(GoogleOAuthCredentials.read(empty).present()).isFalse();
        assertThat(GoogleOAuthCredentials.read(empty).problem()).isNull();
    }

    @Test
    void explainsWrongFilesWithoutRevealingValues() {
        String installed = WEB.replace("\"web\"", "\"installed\"");
        assertThat(GoogleOAuthCredentials.parse(installed).problem()).contains("Aplicación web").doesNotContain(SECRET);
        assertThat(GoogleOAuthCredentials.parse("{no es json").problem()).contains("no es un JSON válido");
        assertThat(GoogleOAuthCredentials.parse(WEB.replace(SECRET, "")).problem()).contains("client_secret").doesNotContain(ID);
        assertThat(GoogleOAuthCredentials.parse(WEB.replace(ID, "otro-valor")).problem()).contains("client_id").doesNotContain(SECRET);
    }

    @Test
    void productionCallbackFollowsThePublicUrl() {
        assertThat(GoogleOAuthCredentials.callbackFor("https://tienda.ejemplo.com/")).isEqualTo("https://tienda.ejemplo.com/login/oauth2/code/google");
        assertThat(GoogleOAuthCredentials.callbackFor("http://localhost:8080")).isEqualTo("http://localhost:8080/login/oauth2/code/google");
    }

    @Test
    void startupPrefersTheJsonSecretAndKeepsExplicitVariablesFirst() throws Exception {
        Path file = Files.writeString(dir.resolve("google-oauth.json"), WEB);
        MockEnvironment env = new MockEnvironment().withProperty("GOOGLE_OAUTH_JSON_FILE", file.toString());
        new IntegrationEnvironment().postProcessEnvironment(env, null);
        assertThat(env.getProperty("polymer.google.client-id")).isEqualTo(ID);
        assertThat(env.getProperty("polymer.google.client-secret")).isEqualTo(SECRET);
        assertThat(env.getProperty("polymer.google.source")).isEqualTo("secrets/google-oauth.json");
        assertThat(env.getProperty("polymer.google.redirect-uris")).contains("http://127.0.0.1:8080/login/oauth2/code/google");

        MockEnvironment mock = new MockEnvironment().withProperty("GOOGLE_OAUTH_JSON_FILE", file.toString())
                .withProperty("GOOGLE_CLIENT_ID", "proveedor-de-pruebas").withProperty("GOOGLE_CLIENT_SECRET", "secreto-de-pruebas");
        new IntegrationEnvironment().postProcessEnvironment(mock, null);
        assertThat(mock.getProperty("polymer.google.client-id")).isEqualTo("proveedor-de-pruebas");

        Path broken = Files.writeString(dir.resolve("roto.json"), "{\"installed\":{}}");
        MockEnvironment wrong = new MockEnvironment().withProperty("GOOGLE_OAUTH_JSON_FILE", broken.toString());
        new IntegrationEnvironment().postProcessEnvironment(wrong, null);
        assertThat(wrong.getProperty("polymer.google.client-id")).isEmpty();
        assertThat(wrong.getProperty("polymer.google.problem")).contains("Aplicación web");
    }
}
