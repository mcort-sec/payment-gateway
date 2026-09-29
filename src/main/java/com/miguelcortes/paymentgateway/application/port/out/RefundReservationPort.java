package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.domain.model.Refund;

public interface RefundReservationPort {

    RefundReservationResult reserve(Refund refund);
}
