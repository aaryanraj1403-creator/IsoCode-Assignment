package com.reconflow;

import java.util.Map;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Configuration
@EnableMethodSecurity
class Security {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean @DependsOnDatabaseInitialization
    UserDetailsService users(JdbcTemplate db, Environment env, PasswordEncoder encoder) {
        for (String name : new String[]{"finance", "founder"}) {
            String password = env.getRequiredProperty("DEMO_" + name.toUpperCase() + "_PASSWORD");
            if (password.length() < 12) throw new IllegalArgumentException("Demo passwords require at least 12 characters");
            db.update("INSERT INTO user_account(username,password_hash,role) VALUES (?,?,?) ON CONFLICT(username) DO UPDATE SET password_hash=excluded.password_hash",
                name, encoder.encode(password), name.equals("finance") ? "FINANCE" : "FOUNDER");
        }
        return username -> db.query("SELECT * FROM user_account WHERE username=?", (rs, n) ->
            User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles(rs.getString("role")).build(), username)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }

    @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/health").permitAll()
                .requestMatchers("/api/**").hasAnyRole("FINANCE", "FOUNDER").anyRequest().denyAll())
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((q, r, auth) -> { r.setContentType("application/json"); r.getWriter().write("{\"ok\":true}"); })
                .failureHandler((q, r, e) -> r.sendError(401, "Check your username and password")))
            .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((q, r, auth) -> r.setStatus(204)))
            .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> r.sendError(401))
                .accessDeniedHandler((q,r,x) -> r.sendError(403)))
            .requestCache(c -> c.disable()).build();
    }
}

@RestController @RequestMapping("/api")
class AuthController {
    final Support s;
    AuthController(Support s) { this.s=s; }
    @GetMapping("/auth/csrf") Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @GetMapping("/me") Map<String,Object> me(Authentication auth) {
        return Map.of("username", auth.getName(), "roles", auth.getAuthorities().stream().map(Object::toString).toList());
    }
    @GetMapping("/health") Map<String,String> health() { return Map.of("status", "UP"); }
    @GetMapping("/system-info") Map<String,Object> systemInfo() {
        return Map.of("java",System.getProperty("java.version"),"postgresql",s.db.queryForObject("SHOW server_version",String.class),"logicalProcessors",Runtime.getRuntime().availableProcessors());
    }
}
