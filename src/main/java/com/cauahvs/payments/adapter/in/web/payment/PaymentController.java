package com.cauahvs.payments.adapter.in.web.payment;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.service.GetPaymentService;
import com.cauahvs.payments.domain.Payment;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.core.Authentication;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@Profile({"web", "default"})
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final GetPaymentService getPaymentService;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase,
                              GetPaymentService getPaymentService){
        this.createPaymentUseCase = createPaymentUseCase;
        this.getPaymentService = getPaymentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentResponse> findAll() {
        return getPaymentService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request,
                                                  Authentication authentication) {
        String createdBy = (authentication != null) ? authentication.getName() : "system";

        CreatePaymentCommand command = new CreatePaymentCommand(
                request.payerId(),
                request.payeeId(),
                request.amount(),
                request.currency(),
                createdBy
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
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        PaymentResponse payment = getPaymentService.findById(id);
        return payment != null
                ? ResponseEntity.ok(payment)
                : ResponseEntity.notFound().build();
    }
}
