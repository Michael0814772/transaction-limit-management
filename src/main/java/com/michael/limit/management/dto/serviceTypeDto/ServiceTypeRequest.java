package com.michael.limit.management.dto.serviceTypeDto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@RequiredArgsConstructor
@Validated
public class ServiceTypeRequest {

    @NotNull
    private int serviceTypeId;

    @NotNull
    @NotEmpty
    private String serviceType;

    @NotNull
    @NotEmpty
    private String description;

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType.toUpperCase();
    }

    public void setDescription(String description) {
        this.description = description.toUpperCase();
    }
}
