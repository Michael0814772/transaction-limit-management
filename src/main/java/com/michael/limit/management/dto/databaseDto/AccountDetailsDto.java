package com.michael.limit.management.dto.databaseDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDetailsDto {

    private String firstName;
    private String middleName;
    private String lastName;
    private String gender;
    private String dob;
    private String bvn;
    private String address;
    private String nok;
    private String nokRshp;
    private String state;
    private String country;
    private String id;
    private String idNos;
    private String phoneNumber;
    private String emailAddress;
    private String relationshipManager;
    private String accountSegment;
    private String cifType;
    private int kycLevel;
    private String cifId;
    private String creditStatus;
    private String relationshipManagerName;
}
