package com.cauahvs.payments.adapter.in.web;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Payment;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@Profile({"web", "default"})
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final PaymentRepository paymentRepository;

    public  PaymentController(CreatePaymentUseCase createPaymentUseCase,
                              PaymentRepository paymentRepository){
        this.createPaymentUseCase = createPaymentUseCase;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request){

        CreatePaymentCommand command = new CreatePaymentCommand(
                request.payerId(),
                request.payeeId(),
                request.amount(),
                request.currency()
        );

        Payment payment = createPaymentUseCase.execute(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(payment.id())
                .toUri();

        return ResponseEntity.created(location).body(PaymentResponse.fromDomain(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id){

        return paymentRepository.findById(id)
                .map(PaymentResponse::fromDomain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
