package com.cauahvs.payments.integration;

import com.cauahvs.payments.adapter.in.web.payment.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Seguranca HTTP dos endpoints de pagamento")
class PaymentSecurityIT {

    static {
        AbstractIntegrationTest.POSTGRES.start();
        AbstractIntegrationTest.KAFKA.start();
        AbstractIntegrationTest.REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", AbstractIntegrationTest.KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", AbstractIntegrationTest.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> AbstractIntegrationTest.REDIS.getMappedPort(6379).toString());
    }

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private static final String PAYMENT_BODY = """
            {"payerId":"payer-001","payeeId":"payee-002","amount":100.00,"currency":"BRL"}
            """;

    @Test
    @DisplayName("POST /payments sem token retorna 403")
    void deveRecusarPostSemToken() {
        HttpStatus status = client().post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(PAYMENT_BODY)
                .exchange((req, res) -> HttpStatus.valueOf(res.getStatusCode().value()));

        assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /payments com token valido retorna 201")
    void deveCriarPagamentoComToken() {
        String token = registrarEObterToken("user-" + UUID.randomUUID());

        var response = client().post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(PAYMENT_BODY)
                .retrieve()
                .toEntity(PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdBy()).isNotNull();
    }

    @Test
    @DisplayName("GET /payments com role USER retorna 403 (precisa ADMIN)")
    void deveRecusarListagemParaUser() {
        String token = registrarEObterToken("user-" + UUID.randomUUID());

        HttpStatus status = client().get()
                .uri("/payments")
                .header("Authorization", "Bearer " + token)
                .exchange((req, res) -> HttpStatus.valueOf(res.getStatusCode().value()));

        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private String registrarEObterToken(String username) {
        String body = """
                {"username":"%s","password":"senha123456"}
                """.formatted(username);

        Map<String, Object> response = client().post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("token");
    }
}