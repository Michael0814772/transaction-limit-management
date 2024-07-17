package com.michael.limit.management.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
@Slf4j
public class LastModifiedBy {

    public String lastModifiedBy() {
        String name = "";
        try {
            InetAddress inetAdd = InetAddress.getLocalHost();
            name = inetAdd.getHostName();
        } catch (UnknownHostException u) {
            log.info(" " + u);
        }
        return name;
    }
}
