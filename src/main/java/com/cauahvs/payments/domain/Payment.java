package com.cauahvs.payments.domain;

import java.time.Instant;
import java.util.UUID;

public class Payment {
    private final UUID id;
    private final String payerId;
    private final String payeeId;
    private final Money money;
    private final Instant createdAt;
    private PaymentStatus status;
    private Instant updatedAt;
    private final String createdBy;

    private Payment(UUID id, String payerId, String payeeId, Money money,
                    PaymentStatus status, Instant createdAt, Instant updatedAt, String createdBy) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null.");
        }
        if (payerId == null || payerId.isBlank()) {
            throw new IllegalArgumentException("payerId must not be blank.");
        }
        if (payeeId == null || payeeId.isBlank()) {
            throw new IllegalArgumentException("payeeId must not be blank.");
        }
        if (money == null) {
            throw new IllegalArgumentException("money must not be null.");
        }

        this.id = id;
        this.payerId = payerId;
        this.payeeId = payeeId;
        this.money = money;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }

    public static Payment create(UUID id, String payerId, String payeeId, Money money, String createdBy) {
        Instant now = Instant.now();
        return new Payment(id, payerId, payeeId, money, PaymentStatus.PENDING, now, now, createdBy);
    }

    public static Payment reconstruct(UUID id, String payerId, String payeeId,
                                      Money money, PaymentStatus status,
                                      Instant createdAt, Instant updatedAt, String createdBy) {
        return new Payment(id, payerId, payeeId, money, status, createdAt, updatedAt, createdBy);
    }

    public String createdBy() {
        return createdBy;
    }

    public void startProcessing() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot start processing: payment must be PENDING but is: " + status);
        }
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot complete: payment must be PROCESSING, but is: " + status);
        }
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot fail: payment must be PROCESSING, but is: " + status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public boolean isFinal() {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED;
    }

    public boolean isPending()    { return status == PaymentStatus.PENDING; }
    public boolean isProcessing() { return status == PaymentStatus.PROCESSING; }
    public boolean isCompleted()  { return status == PaymentStatus.COMPLETED; }
    public boolean isFailed()     { return status == PaymentStatus.FAILED; }

    public UUID id()               { return id; }
    public String payerId()        { return payerId; }
    public String payeeId()        { return payeeId; }
    public Money money()           { return money; }
    public PaymentStatus status()  { return status; }
    public Instant createdAt()     { return createdAt; }
    public Instant updatedAt()     { return updatedAt; }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", payerId='" + payerId + '\'' +
                ", payeeId='" + payeeId + '\'' +
                ", money=" + money +
                ", createdAt=" + createdAt +
                ", status=" + status +
                ", updatedAt=" + updatedAt +
                '}';
    }
}