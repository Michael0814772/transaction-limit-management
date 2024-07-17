package com.michael.limit.management.dto.authentication;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ServiceResponseMessage {

    private String responseCode;

    private Integer accessLevel;

    private String responseMsg;
}
