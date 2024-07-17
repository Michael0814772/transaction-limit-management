package com.michael.limit.management.dto.productType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@RequiredArgsConstructor
@Validated
public class ProductTypeDto {

    @NotNull
    @NotEmpty
    private String transfer;

    @NotNull
    @NotEmpty
    private String product;

    public void setTransfer(String transfer) {
        this.transfer = transfer.toUpperCase();
    }

    public void setProduct(String product) {
        this.product = product.toUpperCase();
    }
}
