package com.michael.limit.management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferMethodResponse {

    private String status;

    private int destinationRequestId;

    private BigDecimal nipAmount = BigDecimal.ZERO;
}
