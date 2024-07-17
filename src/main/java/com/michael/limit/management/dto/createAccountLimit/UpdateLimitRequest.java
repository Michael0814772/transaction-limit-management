package com.michael.limit.management.dto.createAccountLimit;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.validation.annotation.Validated;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@RequiredArgsConstructor
@Validated
public class UpdateLimitRequest {

    private String accountNumber;

    private String cifId;

    private String cifType; //either RET or CORP

    @NumberFormat
    private int channelCode;

    @NotNull
    @NotEmpty
    private String transferType; //either INSTANT or NON-INSTANT

    private String productType = "NIPS";

    @JsonInclude(NON_NULL)
    @Valid
    private UpdateGlobalLimit globalLimit;

    @JsonInclude(NON_NULL)
    @Valid
    private UpdateNipLimit nipLimit;

    @Valid
    @JsonInclude(NON_NULL)
    private UpdateChannelLimit channelLimits;

    public void setCifType(String cifType) {
        this.cifType = cifType.toUpperCase();
    }

    public void setTransferType(String transferType) {
        this.transferType = transferType.toUpperCase();
    }
}
