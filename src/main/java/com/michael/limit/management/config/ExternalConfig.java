package com.michael.limit.management.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExternalConfig {

    @Value("${call.authentication}")
    public boolean callAuthentication;

    @Value("${vector.bytes}")
    public String vectorBytes;

    @Value("${key.bytes}")
    public String keyBytes;
}
