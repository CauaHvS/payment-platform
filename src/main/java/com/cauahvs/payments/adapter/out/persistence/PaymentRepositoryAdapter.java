package com.cauahvs.payments.adapter.out.persistence;

import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Money;
import com.cauahvs.payments.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = toEntity(payment);
        PaymentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(this::toDomain);
    }

    private PaymentJpaEntity toEntity(Payment payment) {
        return new PaymentJpaEntity(
                payment.id(),
                payment.payerId(),
                payment.payeeId(),
                payment.money().amount(),
                payment.money().currency(),
                payment.status(),
                payment.createdAt(),
                payment.updatedAt()
        );
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        Money money = new Money(entity.getAmount(), entity.getCurrency());
        return Payment.reconstruct(
                entity.getId(),
                entity.getPayerId(),
                entity.getPayeeId(),
                money,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}