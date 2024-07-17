package com.michael.limit.management.dto.updateCustomerHash;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@RequiredArgsConstructor
@Validated
public class UpdateCustomerHash {

    @NotEmpty
    private String accountNumber;

    @NotNull
    @NotEmpty
    private String transferType;

    @NotNull
    @NotEmpty
    private String productType;
}
