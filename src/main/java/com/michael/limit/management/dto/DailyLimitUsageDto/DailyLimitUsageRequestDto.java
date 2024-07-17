package com.michael.limit.management.dto.DailyLimitUsageDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;

@Data
@RequiredArgsConstructor
@Validated
public class DailyLimitUsageRequestDto {

    @Size(min = 10, max = 10, message = "account number must be 10 digits")
    private String accountNumber;

    @NotNull
    private int serviceTypeId;

    private int destinationRequestId; //0 => Internal, 1 => external, 2 => both

    @Valid
    ArrayList<TransferDestinationDetailsDto> transferDestinationDetails = new ArrayList<>();

    @NotNull
    @NotEmpty
    private String requestDestinationId; //=>change to transactionRequestId

    private int channelId;

    private String transferType = "INSTANT";

    private String paymentChannel; //either nips, or any other registered

    private int isBulk; //1 for bulk partial reversal

    public void setPaymentChannel(String paymentChannel) {

        if (paymentChannel != null) {
            if (!paymentChannel.trim().isEmpty()) {
                this.paymentChannel = paymentChannel.trim().toUpperCase();
            }
        }
    }
}
