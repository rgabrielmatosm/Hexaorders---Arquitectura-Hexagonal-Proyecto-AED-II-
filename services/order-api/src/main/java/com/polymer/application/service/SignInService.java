package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.NotificationUnavailableException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.ChallengeView;
import com.polymer.application.port.in.SignInStep;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.application.port.out.AccountSecurity;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.DeviceTokens;
import com.polymer.application.port.out.DeviceTokens.Kind;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.PasswordHasher;
import com.polymer.application.port.out.UserPreferences;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.port.out.VerificationChallenges;
import com.polymer.domain.identity.ExternalIdentity;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.identity.VerificationChallenge;
import com.polymer.domain.identity.VerificationPolicy;
import com.polymer.domain.identity.VerificationPurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inicio de sesión en dos pasos y sesiones recordadas. El adaptador web comprueba el primer factor;
 * aquí se decide si se pide el código, se genera, se guarda solo su hash y se envía por correo.
 */
public final class SignInService implements SignInUseCases {
    static final String GOOGLE = "google";
    private final UserRepository users;
    private final AccountSecurity security;
    private final VerificationChallenges challenges;
    private final DeviceTokens tokens;
    private final PasswordHasher hasher;
    private final MailSender mail;
    private final UserPreferences preferences;
    private final AuditLog audit;
    private final Clock clock;
    private final SecureRandom random;
    private final boolean sandboxMail;

    /** mail == null: sin servidor de correo (modo basic); no se piden códigos. */
    public SignInService(UserRepository users, AccountSecurity security, VerificationChallenges challenges, DeviceTokens tokens,
                         PasswordHasher hasher, MailSender mail, UserPreferences preferences, AuditLog audit, Clock clock,
                         SecureRandom random, boolean sandboxMail) {
        this.users = users;
        this.security = security;
        this.challenges = challenges;
        this.tokens = tokens;
        this.hasher = hasher;
        this.mail = mail;
        this.preferences = preferences;
        this.audit = audit;
        this.clock = clock;
        this.random = random;
        this.sandboxMail = sandboxMail;
    }

    @Override
    public boolean verificationEnabled() { return mail != null; }

    @Override
    public SignInStep afterPassword(long userId, String trustedDevice) {
        UserAccount account = enabledAccount(userId);
        if (!verificationEnabled()) return SignInStep.complete(userId);
        AccountSecurity.SecurityState state = security.state(userId);
        if (!state.emailVerified()) return new SignInStep(userId, issue(account, VerificationPurpose.EMAIL_VERIFICATION), false);
        if (state.twoFactor() && !trusted(userId, trustedDevice)) {
            return new SignInStep(userId, issue(account, VerificationPurpose.SIGN_IN), false);
        }
        return SignInStep.complete(userId);
    }

    @Override
    public SignInStep withExternalIdentity(ExternalIdentity identity, String trustedDevice) {
        if (!identity.emailVerified()) throw new ForbiddenOperationException("Google no ha verificado ese correo");
        boolean created = false;
        Optional<Long> linked = security.userForIdentity(identity.provider(), identity.subject());
        long userId;
        if (linked.isPresent()) {
            userId = linked.get();
        } else {
            Optional<UserAccount> byEmail = users.findByEmail(identity.email());
            if (byEmail.isPresent()) {
                userId = byEmail.get().id();
            } else {
                userId = createCustomer(identity).id();
                created = true;
            }
            security.linkIdentity(userId, identity.provider(), identity.subject(), identity.email());
            audit.record(identity.provider(), "IDENTITY_LINKED", "user:" + userId, created ? "cuenta nueva" : "cuenta existente");
        }
        UserAccount account = enabledAccount(userId);
        if (!verificationEnabled() || (!created && trusted(userId, trustedDevice))) {
            if (verificationEnabled()) security.setEmailVerified(userId, true);
            return new SignInStep(userId, null, created);
        }
        return new SignInStep(userId, issue(account, VerificationPurpose.SIGN_IN), created);
    }

    @Override
    public SignInStep verify(UUID challengeId, String code) {
        VerificationChallenge challenge = find(challengeId);
        Instant now = clock.instant();
        if (challenge.consumed()) throw new BusinessConflictException("CHALLENGE_USED", "Esta verificación ya se completó");
        if (challenge.expired(now)) throw new BusinessConflictException("CODE_EXPIRED", "El código venció: pide uno nuevo");
        if (challenge.attemptsLeft() == 0) throw new BusinessConflictException("TOO_MANY_ATTEMPTS", "Demasiados intentos: pide un código nuevo");
        if (!VerificationPolicy.wellFormed(code)) throw new IllegalArgumentException("code: escribe los 6 dígitos del código");
        if (!hasher.matches(VerificationPolicy.normalize(code), challenge.codeHash())) {
            VerificationChallenge failed = challenge.afterFailedAttempt();
            challenges.save(failed);
            audit.record("user:" + challenge.userId(), "VERIFICATION_FAILED", "challenge:" + challengeId, "quedan " + failed.attemptsLeft());
            if (failed.attemptsLeft() == 0) throw new BusinessConflictException("TOO_MANY_ATTEMPTS", "Demasiados intentos: pide un código nuevo");
            throw new IllegalArgumentException("code: el código no es correcto");
        }
        challenges.save(challenge.consume());
        enabledAccount(challenge.userId());
        security.setEmailVerified(challenge.userId(), true); // el código llegó a ese correo: el correo es suyo
        audit.record("user:" + challenge.userId(), "VERIFICATION_SUCCEEDED", "challenge:" + challengeId, challenge.purpose().name());
        return SignInStep.complete(challenge.userId());
    }

    @Override
    public ChallengeView resend(UUID challengeId) {
        VerificationChallenge challenge = find(challengeId);
        Instant now = clock.instant();
        if (challenge.consumed()) throw new BusinessConflictException("CHALLENGE_USED", "Esta verificación ya se completó");
        if (!challenge.canResend(now)) throw new BusinessConflictException("RESEND_TOO_SOON", "Espera un momento antes de pedir otro código");
        UserAccount account = enabledAccount(challenge.userId());
        String code = VerificationPolicy.newCode(random);
        VerificationChallenge resent = challenge.resent(hasher.hash(code), now);
        challenges.save(resent);
        send(account, resent.purpose(), code);
        return view(resent, account, now);
    }

    @Override
    public ChallengeView challenge(UUID challengeId) {
        VerificationChallenge challenge = find(challengeId);
        UserAccount account = users.findById(challenge.userId()).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return view(challenge, account, clock.instant());
    }

    @Override
    public String trustDevice(long userId) { return newToken(Kind.TRUSTED_DEVICE, userId); }

    @Override
    public String rememberLogin(long userId) { return newToken(Kind.REMEMBERED_LOGIN, userId); }

    @Override
    public Optional<ResumedLogin> resume(String rememberCookie) {
        String[] parts = split(rememberCookie);
        if (parts == null) return Optional.empty();
        Optional<DeviceTokens.StoredToken> stored = tokens.find(Kind.REMEMBERED_LOGIN, parts[0]);
        if (stored.isEmpty()) return Optional.empty();
        DeviceTokens.StoredToken token = stored.get();
        if (!clock.instant().isBefore(token.expiresAt())) {
            tokens.delete(Kind.REMEMBERED_LOGIN, token.series());
            return Optional.empty();
        }
        if (!sameHash(parts[1], token.tokenHash())) {
            // Serie conocida con ficha antigua: alguien reutilizó una cookie robada. Se cierran todas sus sesiones recordadas.
            tokens.deleteAllFor(Kind.REMEMBERED_LOGIN, token.userId());
            audit.record("user:" + token.userId(), "REMEMBERED_LOGIN_REVOKED", "series:" + token.series(), "ficha reutilizada");
            return Optional.empty();
        }
        Optional<UserAccount> account = users.findById(token.userId()).filter(UserAccount::enabled);
        if (account.isEmpty()) {
            tokens.delete(Kind.REMEMBERED_LOGIN, token.series());
            return Optional.empty();
        }
        String fresh = randomPart(32);
        tokens.rotate(Kind.REMEMBERED_LOGIN, token.series(), sha256(fresh), token.expiresAt());
        return Optional.of(new ResumedLogin(token.userId(), token.series() + "." + fresh));
    }

    @Override
    public void forget(String rememberCookie) {
        String[] parts = split(rememberCookie);
        if (parts != null) tokens.delete(Kind.REMEMBERED_LOGIN, parts[0]);
    }

    @Override
    public UserAccount account(long userId) { return enabledAccount(userId); }

    @Override
    public void forgetAllDevices(AuthenticatedUser user) {
        if (user == null) throw new ForbiddenOperationException("Sesión requerida");
        tokens.deleteAllFor(Kind.REMEMBERED_LOGIN, user.userId());
        tokens.deleteAllFor(Kind.TRUSTED_DEVICE, user.userId());
        audit.record(user.username(), "DEVICES_FORGOTTEN", "user:" + user.username(), "");
    }

    @Override
    public void setTwoFactor(AuthenticatedUser user, boolean enabled) {
        if (user == null) throw new ForbiddenOperationException("Sesión requerida");
        if (enabled && !verificationEnabled()) {
            throw new BusinessConflictException("MAIL_UNAVAILABLE", "La verificación en dos pasos necesita el servidor de correo");
        }
        security.setTwoFactor(user.userId(), enabled);
        audit.record(user.username(), enabled ? "TWO_FACTOR_ENABLED" : "TWO_FACTOR_DISABLED", "user:" + user.username(), "");
    }

    @Override
    public SecuritySettings securitySettings(AuthenticatedUser user) {
        if (user == null) throw new ForbiddenOperationException("Sesión requerida");
        AccountSecurity.SecurityState state = security.state(user.userId());
        return new SecuritySettings(state.emailVerified(), state.twoFactor(), state.googleLinked());
    }

    // ---- internos --------------------------------------------------------------------------------

    private UserAccount enabledAccount(long userId) {
        UserAccount account = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!account.enabled()) throw new ForbiddenOperationException("La cuenta está desactivada");
        return account;
    }

    private UserAccount createCustomer(ExternalIdentity identity) {
        String base = identity.suggestedUsername();
        String username = base;
        for (int n = 2; users.usernameExists(username); n++) {
            String suffix = String.valueOf(n);
            username = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()) : base) + suffix;
        }
        // Sin contraseña utilizable: entra con Google (puede crear una desde «Olvidé…» en el futuro).
        String unusable = hasher.hash(randomPart(48));
        UserAccount account = users.create(username, identity.displayName(), identity.email(), unusable, EnumSet.of(Role.CUSTOMER));
        if (verificationEnabled()) security.setEmailVerified(account.id(), false);
        audit.record(identity.provider(), "USER_REGISTERED", "user:" + username, "CUSTOMER · " + identity.provider());
        return account;
    }

    private ChallengeView issue(UserAccount account, VerificationPurpose purpose) {
        Instant now = clock.instant();
        String code = VerificationPolicy.newCode(random);
        VerificationChallenge challenge = VerificationChallenge.issue(UUID.randomUUID(), account.id(), purpose, hasher.hash(code), now);
        challenges.save(challenge);
        send(account, purpose, code);
        audit.record("user:" + account.id(), "VERIFICATION_SENT", "challenge:" + challenge.id(), purpose.name());
        return view(challenge, account, now);
    }

    private void send(UserAccount account, VerificationPurpose purpose, String code) {
        Map<String, String> text = VerificationTexts.of(preferences.language(account.id()));
        String minutes = String.valueOf(VerificationPolicy.CODE_TTL.toMinutes());
        String reason = text.get(purpose == VerificationPurpose.EMAIL_VERIFICATION ? "email" : "signIn");
        String greeting = text.get("greeting").replace("{name}", account.displayName());
        String validity = text.get("validity").replace("{minutes}", minutes);
        String plain = greeting + "\n\n" + reason + "\n\n    " + code + "\n\n" + validity + "\n\n" + text.get("ignore") + "\n\n"
                + text.get("signature") + (sandboxMail ? "\n\n(Mailpit)" : "") + "\n";
        String html = MailLayout.page(text.get("subject").replace("{code}", code),
                "<p>" + MailLayout.escape(greeting) + "</p><p>" + MailLayout.escape(reason) + "</p>"
                + "<p style=\"font:700 34px/1.2 ui-monospace,Consolas,monospace;letter-spacing:10px;color:#0b1220;background:#eef6ff;"
                + "border:1px solid #c9defc;border-radius:12px;padding:16px 20px;text-align:center;margin:20px 0\">" + code + "</p>"
                + "<p>" + MailLayout.escape(validity) + "</p><p style=\"color:#5b6475;font-size:13px\">" + MailLayout.escape(text.get("ignore")) + "</p>",
                text.get("signature"));
        try {
            mail.send(new MailSender.MailMessage(account.email(), text.get("subject").replace("{code}", code), plain, html));
        } catch (RuntimeException failed) {
            throw new NotificationUnavailableException("No pudimos enviar el código por correo", failed);
        }
    }

    private ChallengeView view(VerificationChallenge challenge, UserAccount account, Instant now) {
        return new ChallengeView(challenge.id(), challenge.purpose(), VerificationPolicy.maskEmail(account.email()), challenge.expiresAt(),
                challenge.attemptsLeft(), challenge.resendAvailableIn(now).toSeconds(), challenge.canResend(now));
    }

    private VerificationChallenge find(UUID id) {
        if (id == null) throw new IllegalArgumentException("challengeId: obligatorio");
        return challenges.find(id).orElseThrow(() -> new ResourceNotFoundException("La verificación no existe o ya terminó"));
    }

    private boolean trusted(long userId, String cookie) {
        String[] parts = split(cookie);
        if (parts == null) return false;
        return tokens.find(Kind.TRUSTED_DEVICE, parts[0])
                .filter(t -> t.userId() == userId && clock.instant().isBefore(t.expiresAt()) && sameHash(parts[1], t.tokenHash()))
                .isPresent();
    }

    private String newToken(Kind kind, long userId) {
        String series = randomPart(18);
        String token = randomPart(32);
        tokens.store(kind, series, sha256(token), userId, clock.instant().plus(VerificationPolicy.REMEMBER_FOR));
        return series + "." + token;
    }

    private String randomPart(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String[] split(String cookie) {
        if (cookie == null || cookie.length() > 200) return null;
        String[] parts = cookie.split("\\.");
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]{16,40}") || !parts[1].matches("[A-Za-z0-9_-]{20,80}")) return null;
        return parts;
    }

    private static boolean sameHash(String token, String expectedHash) {
        return MessageDigest.isEqual(sha256(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
