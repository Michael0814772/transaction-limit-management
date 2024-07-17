package com.michael.limit.management.dto.specialLimit;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class SpecialLimitRequestDto {

    @NotEmpty
    private String cifId;

    @NotEmpty
    private String customerName;

    @NotNull
    @NotEmpty
    private String transferType;

    @NotNull
    @NotEmpty
    private String productType;

    private int channelCode;
}
