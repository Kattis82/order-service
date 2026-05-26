package se.iths.kattis.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

// konfigurerar säkerheten för order-service
// alla endpoints kräver JWT utom Swagger
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Swagger nås utan token
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        // alla andra endpoints kräver att man är inloggad med JWT
                        .anyRequest().authenticated()
                )
                // talar om att JWT-tokens utfärdade av auth-servern valideras och att rollerna ligger
                // i "roles"-claimet
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Talar om för Spring att rollerna ligger i roles-claimet
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        // Rollerna har redan ROLE_-prefix från auth-servern
        // så prefix sätts till tomt för att undvika ROLE_ROLE_USER
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
