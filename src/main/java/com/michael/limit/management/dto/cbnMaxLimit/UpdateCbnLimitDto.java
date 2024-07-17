package com.michael.limit.management.dto.cbnMaxLimit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCbnLimitDto {

    @NumberFormat
    private int kycLevel;

    @NotNull
    @NotEmpty
    private String cifType; //either RET or CORP

    @NotNull
    @NotEmpty
    private String transferType; //either INSTANT or NON-INSTANT

    @NotNull
    @NotEmpty
    private String productType;

    @Digits(integer = 12, fraction = 2)
    private BigDecimal globalDailyLimit;

    @Digits(integer = 12, fraction = 2)
    private BigDecimal globalPerTransaction;

    public void setCifType(String cifType) {
        if (cifType != null) {
            this.cifType = cifType.toUpperCase();
        }
    }

    public void setTransferType(String transferType) {
        if (transferType != null) {
            this.transferType = transferType;
        }
    }
}
