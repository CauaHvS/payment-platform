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

    public Payment(UUID id, String payerId, String payeeId, Money money) {
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
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void startProcessing(){
        if (status != PaymentStatus.PENDING){
            throw new IllegalStateException("Cannot start processing: payment must be PENDING but is: "+ status);
        }
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete(){
        if (status != PaymentStatus.PROCESSING){
            throw new IllegalStateException("Cannot complete: payment must be PROCESSING, but is: "+ status);
        }
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        if (status != PaymentStatus.PROCESSING){
            throw new IllegalStateException("Cannot fail: payment must be PROCESSING, but is: "+ status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public boolean isPending(){
        return status == PaymentStatus.PENDING;
    }

    public boolean isProcessing(){
        return status == PaymentStatus.PROCESSING;
    }

    public boolean isCompleted(){
        return status == PaymentStatus.COMPLETED;
    }

    public boolean isFailed(){
        return status == PaymentStatus.FAILED;
    }

    public UUID id(){ return id;}

    public String payerId(){ return payerId;}

    public String payeeId(){return payeeId;}

    public Money money(){return money;}

    public PaymentStatus status(){return status;}

    public Instant createdAt(){return createdAt;}

    public Instant updatedAt(){return updatedAt;}

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
