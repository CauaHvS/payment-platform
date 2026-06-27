package com.cauahvs.payments.adapter.in.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Injeta os valores que normalmente viriam do application.yaml via @Value
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-secret-key-com-pelo-menos-256-bits-para-hs256-funcionar-ok");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L); // 1 hora

        user = new User("caua", "senha",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void deveGerarTokenEExtrairUsername() {
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("caua");
    }

    @Test
    void deveValidarTokenDoMesmoUsuario() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void deveRejeitarTokenDeOutroUsuario() {
        String token = jwtService.generateToken(user);

        UserDetails outroUsuario = new User("outro", "senha",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(jwtService.isTokenValid(token, outroUsuario)).isFalse();
    }

    @Test
    void deveRejeitarTokenExpirado() {
        // expiração negativa: token já nasce expirado
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        String tokenExpirado = jwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.isTokenValid(tokenExpirado, user))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deveLancarExcecao_quandoTokenEhAdulterado() {
        String token = jwtService.generateToken(user);
        String adulterado = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.extractUsername(adulterado))
                .isInstanceOf(Exception.class);
    }
}