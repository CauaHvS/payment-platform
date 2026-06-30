package com.cauahvs.payments.integration;

import com.cauahvs.payments.adapter.in.web.payment.PaymentResponse;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.application.service.GetPaymentService;
import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Cache Redis do GET de pagamento")
class PaymentCacheIT extends AbstractIntegrationTest {

    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private GetPaymentService getPaymentService;
    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("findById de pagamento finalizado popula o cache Redis")
    void deveCachearPagamentoFinalizado() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                "payer-001", "payee-002",
                new BigDecimal("100.00"), "BRL", "test-user");
        UUID paymentId = createPaymentUseCase.execute(command).id();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment p = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(p.status()).isIn(PaymentStatus.COMPLETED, PaymentStatus.FAILED);
                });

        Cache cache = cacheManager.getCache("payments");
        assertThat(cache).isNotNull();

        cache.evict(paymentId);

        PaymentResponse response = getPaymentService.findById(paymentId);
        assertThat(response).isNotNull();

        Cache.ValueWrapper cached = cache.get(paymentId);
        assertThat(cached).isNotNull();
        assertThat(cached.get()).isInstanceOf(PaymentResponse.class);

        PaymentResponse cachedResponse = (PaymentResponse) cached.get();
        assertThat(cachedResponse.id()).isEqualTo(paymentId);
    }
}