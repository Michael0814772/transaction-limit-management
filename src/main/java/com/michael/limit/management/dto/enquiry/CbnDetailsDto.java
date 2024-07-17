package com.michael.limit.management.dto.enquiry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CbnDetailsDto {

    private BigDecimal CBNMaxLimitRetail;

    private BigDecimal CBNMaxLimitSME;

    private BigDecimal CBNperTransLimitRetail;

    private BigDecimal CBNperTransLimitSME;
}
