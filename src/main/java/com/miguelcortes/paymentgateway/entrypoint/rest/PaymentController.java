package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.usecase.ApprovePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CancelPaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclinePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetPaymentUseCase;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.entrypoint.rest.dto.CreatePaymentRequest;
import com.miguelcortes.paymentgateway.entrypoint.rest.dto.PaymentResponse;
import com.miguelcortes.paymentgateway.infrastructure.security.MerchantPrincipal;
import com.miguelcortes.paymentgateway.infrastructure.security.ProcessorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@Validated
public class PaymentController {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final DeclinePaymentUseCase declinePaymentUseCase;
    private final CancelPaymentUseCase cancelPaymentUseCase;

    public PaymentController(
            CreatePaymentUseCase createPaymentUseCase,
            GetPaymentUseCase getPaymentUseCase,
            ApprovePaymentUseCase approvePaymentUseCase,
            DeclinePaymentUseCase declinePaymentUseCase,
            CancelPaymentUseCase cancelPaymentUseCase
    ) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.declinePaymentUseCase = declinePaymentUseCase;
        this.cancelPaymentUseCase = cancelPaymentUseCase;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal MerchantPrincipal principal,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key header cannot be blank")
            @Size(max = 64, message = "Idempotency-Key must not exceed 64 characters")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        CreatePaymentCommand command = new CreatePaymentCommand(
                principal.merchantId(),
                request.amount(),
                request.currency(),
                idempotencyKey
        );

        Payment payment = createPaymentUseCase.execute(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(payment.getId())
                .toUri();

        return ResponseEntity.created(location).body(PaymentResponse.fromDomain(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @AuthenticationPrincipal MerchantPrincipal principal,
            @PathVariable UUID id
    ) {
        Payment payment = getPaymentUseCase.execute(id, principal.merchantId());
        return ResponseEntity.ok(PaymentResponse.fromDomain(payment));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @AuthenticationPrincipal ProcessorPrincipal principal,
            @PathVariable UUID id
    ) {
        Payment payment = approvePaymentUseCase.execute(id, principal.processorId());
        return ResponseEntity.ok(PaymentResponse.fromDomain(payment));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<PaymentResponse> declinePayment(
            @AuthenticationPrincipal ProcessorPrincipal principal,
            @PathVariable UUID id
    ) {
        Payment payment = declinePaymentUseCase.execute(id, principal.processorId());
        return ResponseEntity.ok(PaymentResponse.fromDomain(payment));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @AuthenticationPrincipal MerchantPrincipal principal,
            @PathVariable UUID id
    ) {
        Payment payment = cancelPaymentUseCase.execute(id, principal.merchantId());
        return ResponseEntity.ok(PaymentResponse.fromDomain(payment));
    }
}
