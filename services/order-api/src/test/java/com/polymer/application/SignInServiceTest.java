package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.NotificationUnavailableException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.SignInStep;
import com.polymer.application.port.out.AccountSecurity;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.DeviceTokens;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.PasswordHasher;
import com.polymer.application.port.out.StoredCredentials;
import com.polymer.application.port.out.UserPreferences;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.port.out.VerificationChallenges;
import com.polymer.application.service.SignInService;
import com.polymer.domain.identity.ExternalIdentity;
import com.polymer.domain.identity.ExternalSignInIntent;
import com.polymer.domain.identity.Language;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.identity.VerificationChallenge;
import com.polymer.domain.identity.VerificationPurpose;
import com.polymer.support.MutableClock;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** v7: inicio de sesión en dos pasos (contraseña o Google + código por correo) y «Recordarme 30 días». */
class SignInServiceTest {
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private MutableClock clock;
    private InMemoryUsers users;
    private InMemorySecurity security;
    private InMemoryChallenges challenges;
    private InMemoryTokens tokens;
    private List<MailSender.MailMessage> outbox;
    private SignInService signIn;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-13T15:00:00Z"));
        users = new InMemoryUsers();
        security = new InMemorySecurity();
        challenges = new InMemoryChallenges();
        tokens = new InMemoryTokens();
        outbox = new ArrayList<>();
        signIn = service(outbox::add);
    }

    private SignInService service(MailSender mail) {
        PasswordHasher plain = new PasswordHasher() {
            @Override public String hash(String raw) { return "h:" + raw; }
            @Override public boolean matches(String raw, String hash) { return ("h:" + raw).equals(hash); }
        };
        UserPreferences spanish = new UserPreferences() {
            @Override public Language language(long userId) { return Language.ES; }
            @Override public void setLanguage(long userId, Language language) {}
        };
        AuditLog audit = new AuditLog() {
            @Override public void record(String actor, String action, String target, String details) {}
            @Override public List<com.polymer.domain.audit.AuditEvent> recent(int limit) { return List.of(); }
        };
        return new SignInService(users, security, challenges, tokens, plain, mail, spanish, audit, clock, new SecureRandom(), true);
    }

    private String lastCode() {
        Matcher matcher = CODE.matcher(outbox.get(outbox.size() - 1).subject());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void passwordSignInIsCompleteForVerifiedAccountsWithoutTwoFactor() {
        long id = users.add("cliente", "cliente@polymer.local");
        assertThat(signIn.afterPassword(id, null).completed()).isTrue();
        assertThat(outbox).isEmpty();
    }

    @Test
    void anUnverifiedEmailNeedsTheCodeAndTheCodeVerifiesIt() {
        long id = users.add("nuevo", "nuevo@gmail.com");
        security.verified.put(id, false);
        SignInStep step = signIn.afterPassword(id, null);
        assertThat(step.completed()).isFalse();
        assertThat(step.challenge().purpose()).isEqualTo(VerificationPurpose.EMAIL_VERIFICATION);
        assertThat(step.challenge().maskedEmail()).isEqualTo("nu•••@gmail.com");
        MailSender.MailMessage mail = outbox.get(0);
        assertThat(mail.to()).isEqualTo("nuevo@gmail.com");
        assertThat(mail.subject()).matches("\\d{6} es tu código de verificación de POLYMER");
        assertThat(mail.html()).contains(lastCode()).contains("10 minutos");
        // El código en claro nunca se guarda.
        assertThat(challenges.saved.get(step.challenge().id()).codeHash()).isNotEqualTo(lastCode());
        assertThat(signIn.verify(step.challenge().id(), lastCode()).completed()).isTrue();
        assertThat(security.verified.get(id)).isTrue();
        assertThatThrownBy(() -> signIn.verify(step.challenge().id(), lastCode())).isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("ya se completó");
    }

    @Test
    void wrongCodesAreLimitedToFiveAttempts() {
        long id = users.add("ana", "ana@gmail.com");
        security.twoFactor.put(id, true);
        SignInStep step = signIn.afterPassword(id, null);
        String wrong = lastCode().equals("000000") ? "111111" : "000000";
        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> signIn.verify(step.challenge().id(), wrong)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es correcto");
        }
        assertThat(signIn.challenge(step.challenge().id()).attemptsLeft()).isEqualTo(1);
        assertThatThrownBy(() -> signIn.verify(step.challenge().id(), wrong)).isInstanceOf(BusinessConflictException.class).hasMessageContaining("Demasiados");
        assertThatThrownBy(() -> signIn.verify(step.challenge().id(), lastCode())).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> signIn.verify(step.challenge().id(), "12ab")).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void codesExpireAfterTenMinutesAndCanBeResentAfterSixtySeconds() {
        long id = users.add("ana", "ana@gmail.com");
        security.twoFactor.put(id, true);
        SignInStep step = signIn.afterPassword(id, null);
        String first = lastCode();
        assertThatThrownBy(() -> signIn.resend(step.challenge().id())).hasMessageContaining("Espera");
        clock.advance(Duration.ofSeconds(61));
        signIn.resend(step.challenge().id());
        String second = lastCode();
        assertThat(outbox).hasSize(2);
        if (!first.equals(second)) assertThatThrownBy(() -> signIn.verify(step.challenge().id(), first)).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> signIn.verify(step.challenge().id(), second)).hasMessageContaining("venció");
    }

    @Test
    void googleCreatesAVerifiedCustomerAfterTheCodeAndLinksTheSameSubjectNextTime() {
        ExternalIdentity google = new ExternalIdentity("google", "sub-123", "Carla.Diaz@gmail.com", true, "Carla Díaz");
        SignInStep step = signIn.withExternalIdentity(google, null);
        assertThat(step.newAccount()).isTrue();
        assertThat(step.completed()).isFalse();
        UserAccount created = users.findById(step.userId()).orElseThrow();
        assertThat(created.username()).isEqualTo("carla_diaz");
        assertThat(created.roles()).containsExactly(Role.CUSTOMER);
        assertThat(security.verified.get(created.id())).isFalse();
        signIn.verify(step.challenge().id(), lastCode());
        assertThat(security.verified.get(created.id())).isTrue();
        // Segunda vez: misma cuenta, de nuevo con código (dispositivo no recordado).
        SignInStep again = signIn.withExternalIdentity(google, null);
        assertThat(again.userId()).isEqualTo(created.id());
        assertThat(again.newAccount()).isFalse();
        assertThat(again.completed()).isFalse();
    }

    @Test
    void googleLinksAnExistingAccountByEmailAndATrustedDeviceSkipsTheCode() {
        long id = users.add("cliente", "cliente@gmail.com");
        String device = signIn.trustDevice(id);
        SignInStep step = signIn.withExternalIdentity(new ExternalIdentity("google", "sub-9", "CLIENTE@gmail.com", true, "Cliente"), device);
        assertThat(step.userId()).isEqualTo(id);
        assertThat(step.completed()).isTrue();
        assertThat(security.identities).containsEntry("google:sub-9", id);
        // Un dispositivo de otra cuenta, alterado o vencido no sirve.
        long other = users.add("otro", "otro@gmail.com");
        security.twoFactor.put(other, true);
        assertThat(signIn.afterPassword(other, device).completed()).isFalse();
        assertThat(signIn.afterPassword(other, device.replace('.', '-')).completed()).isFalse();
        clock.advance(Duration.ofDays(31));
        security.twoFactor.put(id, true);
        assertThat(signIn.afterPassword(id, device).completed()).isFalse();
    }

    @Test
    void signInWithGoogleRefusesPeopleWithoutAPolymerAccountButSignUpCreatesIt() {
        ExternalIdentity stranger = new ExternalIdentity("google", "sub-new", "nuevo@gmail.com", true, "Nuevo");
        assertThatThrownBy(() -> signIn.withExternalIdentity(stranger, null, ExternalSignInIntent.SIGN_IN))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(error -> assertThat(((BusinessConflictException) error).code()).isEqualTo("NO_ACCOUNT"));
        assertThat(users.findByEmail("nuevo@gmail.com")).isEmpty();
        assertThat(security.identities).doesNotContainKey("google:sub-new");
        // «Registrarse con Google»: crea la cuenta y pide el código; después «Iniciar sesión con Google» ya entra.
        SignInStep created = signIn.withExternalIdentity(stranger, null, ExternalSignInIntent.SIGN_UP);
        assertThat(created.newAccount()).isTrue();
        assertThat(created.completed()).isFalse();
        signIn.verify(created.challenge().id(), lastCode());
        SignInStep again = signIn.withExternalIdentity(stranger, null, ExternalSignInIntent.SIGN_IN);
        assertThat(again.userId()).isEqualTo(created.userId());
        // Una cuenta POLYMER existente con el mismo correo también puede iniciar sesión con Google (se vincula).
        long id = users.add("existente", "existente@gmail.com");
        SignInStep linked = signIn.withExternalIdentity(new ExternalIdentity("google", "sub-x", "existente@gmail.com", true, "E"), null, ExternalSignInIntent.SIGN_IN);
        assertThat(linked.userId()).isEqualTo(id);
        assertThat(linked.newAccount()).isFalse();
    }

    @Test
    void googleEmailsMustBeVerifiedByGoogleAndDisabledAccountsCannotSignIn() {
        assertThatThrownBy(() -> signIn.withExternalIdentity(new ExternalIdentity("google", "s", "a@gmail.com", false, "A"), null))
                .isInstanceOf(ForbiddenOperationException.class);
        long id = users.add("baja", "baja@gmail.com");
        users.disable(id);
        assertThatThrownBy(() -> signIn.afterPassword(id, null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rememberedLoginsRotateAndAReusedTokenRevokesAllOfThem() {
        long id = users.add("cliente", "cliente@polymer.local");
        String cookie = signIn.rememberLogin(id);
        String second = signIn.rememberLogin(id);
        var resumed = signIn.resume(cookie).orElseThrow();
        assertThat(resumed.userId()).isEqualTo(id);
        assertThat(resumed.rotatedCookie()).isNotEqualTo(cookie).startsWith(cookie.substring(0, cookie.indexOf('.')));
        // La ficha antigua se reutiliza (posible robo): se cierran TODAS las sesiones recordadas.
        assertThat(signIn.resume(cookie)).isEmpty();
        assertThat(signIn.resume(resumed.rotatedCookie())).isEmpty();
        assertThat(signIn.resume(second)).isEmpty();
        assertThat(signIn.resume("basura")).isEmpty();
        String expiring = signIn.rememberLogin(id);
        clock.advance(Duration.ofDays(30));
        assertThat(signIn.resume(expiring)).isEmpty();
    }

    @Test
    void withoutAMailServerNoCodeIsRequired() {
        SignInService basic = service(null);
        long id = users.add("ana", "ana@gmail.com");
        security.verified.put(id, false);
        security.twoFactor.put(id, true);
        assertThat(basic.verificationEnabled()).isFalse();
        assertThat(basic.afterPassword(id, null).completed()).isTrue();
        assertThatThrownBy(() -> basic.setTwoFactor(new AuthenticatedUser(id, "ana", EnumSet.of(Role.CUSTOMER)), true))
                .hasMessageContaining("correo");
    }

    @Test
    void aMailFailureIsReportedAsUnavailable() {
        SignInService broken = service(message -> { throw new IllegalStateException("smtp caído"); });
        long id = users.add("ana", "ana@gmail.com");
        security.verified.put(id, false);
        assertThatThrownBy(() -> broken.afterPassword(id, null)).isInstanceOf(NotificationUnavailableException.class);
    }

    // ---- Adaptadores en memoria ---------------------------------------------------------------------------

    static final class InMemoryUsers implements UserRepository {
        final Map<Long, UserAccount> byId = new HashMap<>();
        long next = 1;

        long add(String username, String email) {
            return create(username, username, email, "h:x", EnumSet.of(Role.CUSTOMER)).id();
        }

        void disable(long id) {
            UserAccount a = byId.get(id);
            byId.put(id, new UserAccount(a.id(), a.username(), a.displayName(), a.email(), a.roles(), false, a.createdAt()));
        }

        @Override public Optional<UserAccount> findById(long id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<StoredCredentials> credentials(String username) { return Optional.empty(); }
        @Override public Optional<StoredCredentials> credentials(long id) { return Optional.empty(); }
        @Override public Optional<UserAccount> findByEmail(String email) {
            return byId.values().stream().filter(a -> a.email().equalsIgnoreCase(email)).findFirst();
        }
        @Override public boolean usernameExists(String username) { return byId.values().stream().anyMatch(a -> a.username().equalsIgnoreCase(username)); }
        @Override public boolean emailExists(String email, Long excludingId) { return findByEmail(email).isPresent(); }
        @Override public UserAccount create(String username, String displayName, String email, String passwordHash, Set<Role> roles) {
            UserAccount account = new UserAccount(next++, username, displayName, email, roles, true, Instant.EPOCH);
            byId.put(account.id(), account);
            return account;
        }
        @Override public List<UserAccount> list() { return List.copyOf(byId.values()); }
        @Override public List<UserAccount> withRole(Role role) { return List.of(); }
        @Override public Optional<UserAccount> updateRoles(long id, Set<Role> roles) { return Optional.empty(); }
        @Override public Optional<UserAccount> setEnabled(long id, boolean enabled) { return Optional.empty(); }
        @Override public Optional<UserAccount> updateProfile(long id, String displayName, String email) { return Optional.empty(); }
        @Override public void updatePasswordHash(long id, String passwordHash) {}
    }

    static final class InMemorySecurity implements AccountSecurity {
        final Map<Long, Boolean> verified = new HashMap<>();
        final Map<Long, Boolean> twoFactor = new HashMap<>();
        final Map<String, Long> identities = new HashMap<>();

        @Override public SecurityState state(long userId) {
            return new SecurityState(verified.getOrDefault(userId, true), twoFactor.getOrDefault(userId, false),
                    identities.containsValue(userId));
        }
        @Override public void setEmailVerified(long userId, boolean value) { verified.put(userId, value); }
        @Override public void setTwoFactor(long userId, boolean enabled) { twoFactor.put(userId, enabled); }
        @Override public Optional<Long> userForIdentity(String provider, String subject) { return Optional.ofNullable(identities.get(provider + ":" + subject)); }
        @Override public void linkIdentity(long userId, String provider, String subject, String email) { identities.put(provider + ":" + subject, userId); }
    }

    static final class InMemoryChallenges implements VerificationChallenges {
        final Map<UUID, VerificationChallenge> saved = new HashMap<>();
        @Override public void save(VerificationChallenge challenge) { saved.put(challenge.id(), challenge); }
        @Override public Optional<VerificationChallenge> find(UUID id) { return Optional.ofNullable(saved.get(id)); }
    }

    static final class InMemoryTokens implements DeviceTokens {
        final Map<String, StoredToken> saved = new HashMap<>();
        private static String key(Kind kind, String series) { return kind + ":" + series; }
        @Override public void store(Kind kind, String series, String tokenHash, long userId, Instant expiresAt) {
            saved.put(key(kind, series), new StoredToken(series, tokenHash, userId, expiresAt));
        }
        @Override public Optional<StoredToken> find(Kind kind, String series) { return Optional.ofNullable(saved.get(key(kind, series))); }
        @Override public void rotate(Kind kind, String series, String tokenHash, Instant expiresAt) {
            StoredToken old = saved.get(key(kind, series));
            saved.put(key(kind, series), new StoredToken(series, tokenHash, old.userId(), expiresAt));
        }
        @Override public void delete(Kind kind, String series) { saved.remove(key(kind, series)); }
        @Override public void deleteAllFor(Kind kind, long userId) {
            saved.entrySet().removeIf(e -> e.getKey().startsWith(kind + ":") && e.getValue().userId() == userId);
        }
    }
}
