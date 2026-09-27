package se.iths.kattis.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// konfigurerar säkerheten för order-service
// alla endpoints kräver JWT utom Swagger
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // kopplar in CORS-reglerna (definierade längre ner) i säkerhetskedjan,
                // måste ligga här för att webbläsarens "preflight"-anrop (OPTIONS)
                // ska släppas igenom innan JWT-kontrollen kollar token.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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

    // metoden bygger reglerna för vilka webbadresser (origins) som får
    // anropa order-service från en webbläsare och hur de får anropa den,
    // utan detta blockerar webbläsaren (inte servern) alla anrop från frontend,
    // eftersom frontend och order-service körs på olika portar (=olika "origin").
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // vilka adresser (frontend-portar) som får anropa order-service,
        // måste matcha exakt den adress frontend körs på inklusive port
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175"
        ));

        // vilka HTTP-metoder som är tillåtna, OPTIONS måste alltid vara med
        // eftersom webbläsaren skickar ett OPTIONS-anrop ("preflight") före
        // det riktiga anropet, för att fråga "får jag göra detta?"
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // vilka headers som frontend får skicka med, "*" = alla,
        // vilket inkluderar Authorization-headern som bär JWT-token
        config.setAllowedHeaders(List.of("*"));

        // tillåter att "credentials" (i praktiken: Authorization-headern)
        // skickas med i anropet. Måste vara true annars struntar webbläsaren
        // i att skicka med token, även om allt annat är korrekt
        config.setAllowCredentials(true);  // krävs för att Authorization-headern ska släppas igenom

        // kopplar ihop reglerna ovan med alla endpoints ("/**") i order-service
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
