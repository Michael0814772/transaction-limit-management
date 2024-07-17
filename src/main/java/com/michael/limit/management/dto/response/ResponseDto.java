package com.michael.limit.management.dto.response;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ResponseDto {

    private String responseMsg;

    private String responseCode;
}
