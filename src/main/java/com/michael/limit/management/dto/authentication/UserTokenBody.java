package com.michael.limit.management.dto.authentication;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserTokenBody {

    private String acctNum;

    private String cifId;

    private String serviceToken;

    private String serviceIpAddress;

    private String userToken;

    private String userName = "channels-limit-mgmt-service";

    private String channelId;
}
