package com.michael.limit.management.exception.methodNotValid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Violation {

    private final String fieldName;

    private final String responseMsg;
}
