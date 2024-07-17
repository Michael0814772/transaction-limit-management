package com.michael.limit.management.dto.authentication;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ServiceTokenBody {

    private String token;

    private String sourceIpAddress;

}
