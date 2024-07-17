package com.michael.limit.management.dto.enquiry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnquiryRequestDto {

    private String accountNumber;

    private String cifId;

    private String transferType = "INSTANT";

    private String productType = "NIPS";
}
