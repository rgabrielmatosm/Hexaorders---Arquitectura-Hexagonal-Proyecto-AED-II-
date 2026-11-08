package com.polymer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymer.adapter.out.security.ClasspathPasswordBlocklist;
import com.polymer.domain.identity.PasswordPolicy;
import com.polymer.domain.identity.PasswordPolicy.Problem;
import com.polymer.domain.identity.PasswordPolicy.Strength;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private static final Set<String> COMMON = Set.of("password", "qwertyuiop", "contraseña", "123456789012");
    private static final List<String> ANA = List.of("ana_perez", "Ana Pérez", "ana.perez@example.com");

    private static PasswordPolicy.Assessment assess(String password) {
        return PasswordPolicy.assess(password, ANA, COMMON::contains);
    }

    @Test
    void passphrasesAreStrongWithoutForcingSymbols() {
        assertThat(assess("nube cobre faro tigre lento").strength()).isEqualTo(Strength.STRONG);
        assertThat(assess("caballo-azul-bebe-te").acceptable()).isTrue();
    }

    @Test
    void mixedTwelveCharactersAreStrongAndSimpleOnesOnlyFair() {
        assertThat(assess("Tr4m-v1a#Lun").strength()).isEqualTo(Strength.STRONG);
        assertThat(assess("bosquelejano").strength()).isEqualTo(Strength.FAIR);
    }

    @Test
    void rejectsShortCommonPersonalAndRepetitivePasswords() {
        assertThat(assess("Corta1!").problems()).contains(Problem.TOO_SHORT);
        assertThat(assess("Password2026!").problems()).contains(Problem.COMMON);          // núcleo «password»
        assertThat(assess("contraseña").problems()).contains(Problem.COMMON, Problem.TOO_SHORT);
        assertThat(assess("123456789012").problems()).contains(Problem.COMMON);
        assertThat(assess("ana_perez_2026_xyz").problems()).contains(Problem.CONTAINS_PERSONAL_DATA);
        assertThat(assess("aaaaaaaaaaaaaaaa").problems()).contains(Problem.REPETITIVE);
        assertThat(assess("abcdefghijklmn").problems()).contains(Problem.REPETITIVE);
        assertThat(assess("x".repeat(65)).problems()).contains(Problem.TOO_LONG);
        assertThat(assess("ñ".repeat(40)).problems()).contains(Problem.TOO_LONG);               // 80 bytes > límite de BCrypt
        assertThat(assess(null).acceptable()).isFalse();
    }

    @Test
    void requireThrowsWithStableCodes() {
        assertThatThrownBy(() -> PasswordPolicy.require("qwertyuiop12", ANA, COMMON::contains))
                .isInstanceOf(PasswordPolicy.WeakPasswordException.class)
                .satisfies(error -> assertThat(((PasswordPolicy.WeakPasswordException) error).problems()).contains(Problem.COMMON))
                .hasMessageStartingWith("password:");
    }

    @Test
    void sharedBlocklistFileIsLoaded() {
        var blocklist = new ClasspathPasswordBlocklist();
        assertThat(blocklist.size()).isGreaterThan(300);
        assertThat(blocklist.contains("password")).isTrue();
        assertThat(blocklist.contains("contraseña")).isTrue();
        assertThat(blocklist.contains("una frase larga y unica")).isFalse();
    }
}
