package com.michael.limit.management.dto.DailyLimitUsageDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CifDto {

    private String cifId;

    private int kycLevel;

    private String cifType;

    private String freezeCode;

    private String accountSegment;

    private String currency;
}
