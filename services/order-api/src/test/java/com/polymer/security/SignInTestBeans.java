package com.polymer.security;

import com.polymer.adapter.in.web.AuthController.GoogleSignInAvailability;
import com.polymer.adapter.in.web.security.GoogleSignInHandlers;
import com.polymer.adapter.in.web.security.RememberedLoginFilter;
import com.polymer.adapter.in.web.security.SecurityTelemetry;
import com.polymer.adapter.in.web.security.SessionSignIn;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.config.SignInConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.context.SecurityContextRepository;

/** Beans web del inicio de sesión v7 para las pruebas @WebMvcTest (SignInUseCases es un mock de cada prueba). */
@TestConfiguration(proxyBeanMethods = false)
public class SignInTestBeans {
    @Bean SessionSignIn sessionSignIn(SignInUseCases signIn, SecurityContextRepository contexts, SecurityTelemetry telemetry) {
        return new SessionSignIn(signIn, contexts, telemetry);
    }

    @Bean RememberedLoginFilter rememberedLoginFilter(SignInUseCases signIn, SessionSignIn sessions, SecurityTelemetry telemetry) {
        return new RememberedLoginFilter(signIn, sessions, telemetry);
    }

    @Bean FilterRegistrationBean<RememberedLoginFilter> rememberedLoginFilterRegistration(RememberedLoginFilter filter) {
        FilterRegistrationBean<RememberedLoginFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean GoogleSignInHandlers googleSignInHandlers(SignInUseCases signIn, SessionSignIn sessions, SecurityContextRepository contexts,
                                                    SecurityTelemetry telemetry) {
        return new GoogleSignInHandlers(signIn, sessions, contexts, telemetry);
    }

    @Bean SignInConfiguration.GoogleClient googleClient() { return new SignInConfiguration.GoogleClient(null); }

    @Bean GoogleSignInAvailability googleSignInAvailability() { return new GoogleSignInAvailability(false); }
}
