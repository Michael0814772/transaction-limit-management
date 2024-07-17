package com.michael.limit.management.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomizedException {

    private String responseMsg;
    private String responseCode = "99";
}
