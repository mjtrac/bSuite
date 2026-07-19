/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.viewer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Simplified to permit-all: the Viewer's embedded web server (see
 * CounterFxApplication) is bound to localhost only and reachable exclusively
 * from this same process's own JavaFX WebView. Access is already gated by
 * the outer FX shell's own AuthContext-based login — a second in-app login
 * page here would just be redundant friction for a single-user desktop app,
 * not a real security boundary the original two-port web deployment needed.
 * A SecurityFilterChain bean is still required so Spring Security doesn't
 * fall back to its own "authenticate everything" default.
 *
 * CSRF is deliberately left ENABLED (Spring Security's default) rather than
 * disabled: viewer/index.html's filter form still renders a hidden
 * ${_csrf.parameterName}/${_csrf.token} field (unmodified, per the "keep the
 * Viewer's web layer as-is" plan decision) — disabling CSRF stops Spring
 * from populating that request attribute at all, which breaks the template
 * with a SpelEvaluationException rather than just removing the token. This
 * way the template needs no changes, and CSRF is harmless overhead for a
 * server no one but this same process's own WebView ever talks to.
 *
 * @ConditionalOnWebApplication(SERVLET): CounterFxApplication's
 * scanBasePackages covers both com.mjtrac.counter and com.mjtrac.viewer, so
 * plain @SpringBootTest(webEnvironment = NONE) service-layer tests (which
 * don't start a servlet container) would otherwise fail trying to construct
 * this bean — HttpSecurity is only registered in a SERVLET-type context.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ViewerSecurityConfig {

    @Bean
    public SecurityFilterChain viewerFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
