package com.polymer.application.service;

import com.polymer.domain.identity.Language;
import java.util.Map;

/** Textos del correo con el código de verificación, en los idiomas de la tienda. {code}, {name}, {minutes}. */
final class VerificationTexts {
    private VerificationTexts() {}

    static Map<String, String> of(Language language) {
        return switch (language) {
            case ES -> ES;
            case EN -> EN;
            case FR -> FR;
            case IT -> IT;
            case PT -> PT;
            case DE -> DE;
        };
    }

    private static final Map<String, String> ES = Map.of(
            "subject", "{code} es tu código de verificación de POLYMER",
            "greeting", "Hola, {name}:",
            "signIn", "Usa este código para terminar de iniciar sesión en POLYMER:",
            "email", "Usa este código para confirmar tu correo y activar tu cuenta POLYMER:",
            "validity", "El código vence en {minutes} minutos y solo se puede usar una vez.",
            "ignore", "Si no fuiste tú, ignora este correo: nadie podrá entrar sin el código. Nunca te lo pediremos por teléfono ni por chat.",
            "signature", "— Equipo POLYMER · Tecnología que conecta contigo.");

    private static final Map<String, String> EN = Map.of(
            "subject", "{code} is your POLYMER verification code",
            "greeting", "Hi {name},",
            "signIn", "Use this code to finish signing in to POLYMER:",
            "email", "Use this code to confirm your email and activate your POLYMER account:",
            "validity", "The code expires in {minutes} minutes and can only be used once.",
            "ignore", "If this wasn't you, ignore this email: nobody can sign in without the code. We will never ask for it by phone or chat.",
            "signature", "— The POLYMER team · Technology that connects with you.");

    private static final Map<String, String> FR = Map.of(
            "subject", "{code} est votre code de vérification POLYMER",
            "greeting", "Bonjour {name},",
            "signIn", "Utilisez ce code pour terminer votre connexion à POLYMER :",
            "email", "Utilisez ce code pour confirmer votre e-mail et activer votre compte POLYMER :",
            "validity", "Le code expire dans {minutes} minutes et ne peut être utilisé qu'une seule fois.",
            "ignore", "Si ce n'était pas vous, ignorez cet e-mail : personne ne peut se connecter sans le code. Nous ne vous le demanderons jamais par téléphone ni par chat.",
            "signature", "— L'équipe POLYMER · La technologie qui vous connecte.");

    private static final Map<String, String> IT = Map.of(
            "subject", "{code} è il tuo codice di verifica POLYMER",
            "greeting", "Ciao {name},",
            "signIn", "Usa questo codice per completare l'accesso a POLYMER:",
            "email", "Usa questo codice per confermare la tua e-mail e attivare il tuo account POLYMER:",
            "validity", "Il codice scade tra {minutes} minuti e si può usare una sola volta.",
            "ignore", "Se non sei stato tu, ignora questa e-mail: nessuno può accedere senza il codice. Non te lo chiederemo mai per telefono o in chat.",
            "signature", "— Il team POLYMER · Tecnologia che ti connette.");

    private static final Map<String, String> PT = Map.of(
            "subject", "{code} é o seu código de verificação POLYMER",
            "greeting", "Olá, {name}:",
            "signIn", "Use este código para concluir o login na POLYMER:",
            "email", "Use este código para confirmar o seu e-mail e ativar a sua conta POLYMER:",
            "validity", "O código expira em {minutes} minutos e só pode ser usado uma vez.",
            "ignore", "Se não foi você, ignore este e-mail: ninguém consegue entrar sem o código. Nunca o pediremos por telefone ou chat.",
            "signature", "— Equipe POLYMER · Tecnologia que conecta você.");

    private static final Map<String, String> DE = Map.of(
            "subject", "{code} ist dein POLYMER-Bestätigungscode",
            "greeting", "Hallo {name},",
            "signIn", "Verwende diesen Code, um die Anmeldung bei POLYMER abzuschließen:",
            "email", "Verwende diesen Code, um deine E-Mail-Adresse zu bestätigen und dein POLYMER-Konto zu aktivieren:",
            "validity", "Der Code läuft in {minutes} Minuten ab und kann nur einmal verwendet werden.",
            "ignore", "Wenn du das nicht warst, ignoriere diese E-Mail: Ohne den Code kann sich niemand anmelden. Wir fragen nie per Telefon oder Chat danach.",
            "signature", "— Dein POLYMER-Team · Technik, die verbindet.");
}
