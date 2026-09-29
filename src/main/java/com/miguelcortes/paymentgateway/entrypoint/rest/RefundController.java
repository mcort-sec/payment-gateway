package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.miguelcortes.paymentgateway.application.command.CreateRefundCommand;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.usecase.ApproveRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclineRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.ListRefundsUseCase;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.entrypoint.rest.dto.CreateRefundRequest;
import com.miguelcortes.paymentgateway.entrypoint.rest.dto.PagedResponse;
import com.miguelcortes.paymentgateway.entrypoint.rest.dto.RefundResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
public class RefundController {

    private final CreateRefundUseCase createRefundUseCase;
    private final GetRefundUseCase getRefundUseCase;
    private final ListRefundsUseCase listRefundsUseCase;
    private final ApproveRefundUseCase approveRefundUseCase;
    private final DeclineRefundUseCase declineRefundUseCase;

    public RefundController(
            CreateRefundUseCase createRefundUseCase,
            GetRefundUseCase getRefundUseCase,
            ListRefundsUseCase listRefundsUseCase,
            ApproveRefundUseCase approveRefundUseCase,
            DeclineRefundUseCase declineRefundUseCase
    ) {
        this.createRefundUseCase = createRefundUseCase;
        this.getRefundUseCase = getRefundUseCase;
        this.listRefundsUseCase = listRefundsUseCase;
        this.approveRefundUseCase = approveRefundUseCase;
        this.declineRefundUseCase = declineRefundUseCase;
    }

    @GetMapping("/refunds")
    public ResponseEntity<PagedResponse<RefundResponse>> listRefunds(
            @AuthenticationPrincipal MerchantPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageQuery pageQuery = new PageQuery(page, size);
        PageResult<Refund> result = listRefundsUseCase.execute(principal.merchantId(), pageQuery);
        return ResponseEntity.ok(PagedResponse.from(result, RefundResponse::fromDomain));
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal MerchantPrincipal principal,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key header cannot be blank")
            @Size(max = 64, message = "Idempotency-Key must not exceed 64 characters")
            String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request
    ) {
        CreateRefundCommand command = new CreateRefundCommand(
                paymentId,
                principal.merchantId(),
                request.amount(),
                idempotencyKey
        );

        Refund refund = createRefundUseCase.execute(command);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/refunds/{id}")
                .buildAndExpand(refund.getId())
                .toUri();

        return ResponseEntity.created(location).body(RefundResponse.fromDomain(refund));
    }

    @GetMapping("/refunds/{id}")
    public ResponseEntity<RefundResponse> getRefund(
            @PathVariable UUID id,
            @AuthenticationPrincipal MerchantPrincipal principal
    ) {
        Refund refund = getRefundUseCase.execute(id, principal.merchantId());
        return ResponseEntity.ok(RefundResponse.fromDomain(refund));
    }

    @PostMapping("/refunds/{id}/approve")
    public ResponseEntity<RefundResponse> approveRefund(
            @PathVariable UUID id,
            @AuthenticationPrincipal ProcessorPrincipal principal
    ) {
        Refund refund = approveRefundUseCase.execute(id, principal.processorId());
        return ResponseEntity.ok(RefundResponse.fromDomain(refund));
    }

    @PostMapping("/refunds/{id}/decline")
    public ResponseEntity<RefundResponse> declineRefund(
            @PathVariable UUID id,
            @AuthenticationPrincipal ProcessorPrincipal principal
    ) {
        Refund refund = declineRefundUseCase.execute(id, principal.processorId());
        return ResponseEntity.ok(RefundResponse.fromDomain(refund));
    }
}
