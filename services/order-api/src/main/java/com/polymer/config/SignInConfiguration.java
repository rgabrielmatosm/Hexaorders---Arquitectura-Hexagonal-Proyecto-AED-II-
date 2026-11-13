package com.polymer.config;

import com.polymer.adapter.in.web.AuthController.GoogleSignInAvailability;
import com.polymer.adapter.in.web.security.GoogleSignInHandlers;
import com.polymer.adapter.in.web.security.RememberedLoginFilter;
import com.polymer.adapter.in.web.security.SecurityTelemetry;
import com.polymer.adapter.in.web.security.SessionSignIn;
import com.polymer.adapter.out.mail.SmtpMailSender;
import com.polymer.adapter.out.persistence.JdbcAccountSecurity;
import com.polymer.adapter.out.persistence.JdbcDeviceTokens;
import com.polymer.adapter.out.persistence.JdbcVerificationChallenges;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.application.port.out.AccountSecurity;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.DeviceTokens;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.PasswordHasher;
import com.polymer.application.port.out.UserPreferences;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.port.out.VerificationChallenges;
import com.polymer.application.service.SignInService;
import java.security.SecureRandom;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * v7 · Composition root del inicio de sesión: Google (OpenID Connect), código de verificación por
 * correo y «Recordarme durante 30 días». El núcleo (SignInService) no conoce Spring, OAuth ni SMTP.
 */
@Configuration(proxyBeanMethods = false)
public class SignInConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(SignInConfiguration.class);

    @Bean AccountSecurity accountSecurity(JdbcTemplate jdbc) { return new JdbcAccountSecurity(jdbc); }
    @Bean VerificationChallenges verificationChallenges(JdbcTemplate jdbc) { return new JdbcVerificationChallenges(jdbc); }
    @Bean DeviceTokens deviceTokens(JdbcTemplate jdbc) { return new JdbcDeviceTokens(jdbc); }

    /** Correo transaccional (código de verificación). null sin servidor de correo: modo basic. */
    @Bean
    MailSenderHolder transactionalMail(ObjectProvider<JavaMailSender> smtp,
                                       @Value("${polymer.integrations.mail.enabled:false}") boolean enabled,
                                       @Value("${polymer.mail.from:Polymer <no-reply@polymer.local>}") String from) {
        JavaMailSender sender = smtp.getIfAvailable();
        return new MailSenderHolder(enabled && sender != null ? new SmtpMailSender(sender, from) : null);
    }

    @Bean
    SignInUseCases signInUseCases(UserRepository users, AccountSecurity security, VerificationChallenges challenges, DeviceTokens tokens,
                                  PasswordHasher hasher, MailSenderHolder mail, UserPreferences preferences, AuditLog audit, Clock clock,
                                  @Value("${polymer.mail.sandbox:true}") boolean sandbox) {
        if (mail.sender() == null) LOG.info("Sin servidor de correo: el código de verificación por correo está desactivado");
        return new SignInService(users, security, challenges, tokens, hasher, mail.sender(), preferences, audit, clock, new SecureRandom(), sandbox);
    }

    @Bean
    SessionSignIn sessionSignIn(SignInUseCases signIn, SecurityContextRepository contexts, SecurityTelemetry telemetry) {
        return new SessionSignIn(signIn, contexts, telemetry);
    }

    @Bean
    RememberedLoginFilter rememberedLoginFilter(SignInUseCases signIn, SessionSignIn sessions, SecurityTelemetry telemetry) {
        return new RememberedLoginFilter(signIn, sessions, telemetry);
    }

    /** Solo dentro de la cadena de Spring Security (después de cargar la sesión), no como filtro global del contenedor. */
    @Bean
    FilterRegistrationBean<RememberedLoginFilter> rememberedLoginFilterRegistration(RememberedLoginFilter filter) {
        FilterRegistrationBean<RememberedLoginFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    GoogleSignInHandlers googleSignInHandlers(SignInUseCases signIn, SessionSignIn sessions, SecurityContextRepository contexts,
                                              SecurityTelemetry telemetry) {
        return new GoogleSignInHandlers(signIn, sessions, contexts, telemetry);
    }

    /**
     * Registro de cliente de Google. Las URL se pueden sustituir (POLYMER_GOOGLE_*) para probar el flujo
     * completo contra un proveedor OpenID Connect de pruebas en el CI; por defecto son las de Google.
     */
    @Bean
    GoogleClient googleClient(@Value("${polymer.google.client-id:}") String clientId,
                              @Value("${polymer.google.client-secret:}") String clientSecret,
                              @Value("${POLYMER_GOOGLE_AUTHORIZATION_URI:https://accounts.google.com/o/oauth2/v2/auth}") String authorizationUri,
                              @Value("${POLYMER_GOOGLE_TOKEN_URI:https://oauth2.googleapis.com/token}") String tokenUri,
                              @Value("${POLYMER_GOOGLE_JWK_SET_URI:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri,
                              @Value("${POLYMER_GOOGLE_ISSUER_URI:https://accounts.google.com}") String issuerUri,
                              @Value("${POLYMER_GOOGLE_USER_INFO_URI:https://openidconnect.googleapis.com/v1/userinfo}") String userInfoUri,
                              @Value("${POLYMER_GOOGLE_REDIRECT_URI:}") String fixedRedirectUri,
                              @Value("${POLYMER_PUBLIC_URL:http://localhost:8080}") String publicUrl,
                              @Value("${polymer.google.source:}") String source,
                              @Value("${polymer.google.redirect-uris:}") String registeredUris,
                              @Value("${polymer.google.problem:}") String problem) {
        if (!problem.isBlank()) LOG.warn("«Continuar con Google»: {}", problem);
        if (clientId.isBlank() || clientSecret.isBlank()) {
            LOG.info("«Continuar con Google» no configurado. Para activarlo: descarga el JSON del cliente OAuth "
                    + "(Google Cloud → Google Auth Platform → Clientes → Aplicación web), guárdalo como secrets/google-oauth.json "
                    + "y ejecuta .\\polymer.cmd google (guía: docs/final/GUIA_GOOGLE_Y_PAGOS.md)");
            return new GoogleClient(null);
        }
        // v8 · URI de retorno: dinámica ({baseUrl}: sirve para localhost y 127.0.0.1) o fija para producción (AWS con HTTPS).
        String redirectUri = fixedRedirectUri.isBlank() ? "{baseUrl}/login/oauth2/code/{registrationId}" : fixedRedirectUri.strip();
        String expected = fixedRedirectUri.isBlank() ? GoogleOAuthCredentials.callbackFor(publicUrl) : fixedRedirectUri.strip();
        java.util.List<String> registered = registeredUris.isBlank() ? java.util.List.of() : java.util.List.of(registeredUris.split(","));
        if (!registered.isEmpty() && !registered.contains(expected)) {
            LOG.warn("«Continuar con Google»: el JSON no registra la URI de retorno {} (POLYMER_PUBLIC_URL). Añádela en Google Cloud "
                    + "→ Clientes → URI de redireccionamiento autorizados y descarga el JSON de nuevo; si no, Google responderá redirect_uri_mismatch", expected);
        }
        ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("google")
                .clientName("Google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .jwkSetUri(jwkSetUri)
                .userNameAttributeName(IdTokenClaimNames.SUB);
        if (!issuerUri.isBlank()) builder.issuerUri(issuerUri);
        if (!userInfoUri.isBlank()) builder.userInfoUri(userInfoUri);
        LOG.info("«Continuar con Google» activado (credenciales: {}; {} URI de retorno registradas)", source, registered.size());
        return new GoogleClient(builder.build());
    }

    @Bean
    GoogleSignInAvailability googleSignInAvailability(GoogleClient google) { return new GoogleSignInAvailability(google.registration() != null); }

    /** Contenedor del registro de Google (null = no configurado). */
    public record GoogleClient(ClientRegistration registration) {}

    /** Contenedor del adaptador de correo (null = sin servidor de correo). */
    public record MailSenderHolder(MailSender sender) {}
}
