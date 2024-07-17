package com.michael.limit.management.dto.createAccountLimit;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@RequiredArgsConstructor
@Validated
public class DeleteLimitRequest {

    private String accountNumber;

    private String cifId;

    private int channelId;

    @NotNull
    @NotEmpty
    private String transferType;

    @NotNull
    @NotEmpty
    private String productType;
}
