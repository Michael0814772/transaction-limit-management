package com.michael.limit.management.dto.authentication;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ResponseMessage {

    private String responseCode;

    private Integer serviceAccessLevel;

    private Integer userAccessLevel;
}
