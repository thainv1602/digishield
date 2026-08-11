package com.digishield;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Signs and verifies tokens locally so the {@code dev-secure} profile can
 * enforce authorization without an identity provider.
 *
 * <p>Authorization has only ever run in production: {@code @EnableMethodSecurity}
 * and the RLS aspect are {@code @Profile("!dev")}, and {@code SecurityConfig}
 * locks the API when no issuer is configured. So a local run either has no
 * security at all ({@code dev}) or needs Cognito reachable. Neither lets a
 * developer or a test see a 403.
 *
 * <p>The key pair is generated at startup and never leaves memory, so tokens
 * from one run cannot be replayed against another. Restricted to
 * {@code dev-secure}; production keeps discovering its issuer.
 */
@Configuration
@Profile("dev-secure")
class DevSecureJwtConfig {

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId = UUID.randomUUID().toString();

    DevSecureJwtConfig() {
        KeyPair pair = generate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA unavailable — cannot run dev-secure", e);
        }
    }

    /**
     * Consumed by {@code SecurityConfig}, which prefers a supplied decoder over
     * discovering one from {@code issuer-uri}.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /** Used by {@link DevSecureAuthProvider} to mint tokens on login. */
    @Bean
    JwtEncoder jwtEncoder() {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
}
