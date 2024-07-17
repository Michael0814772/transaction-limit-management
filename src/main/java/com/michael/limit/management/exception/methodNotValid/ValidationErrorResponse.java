package com.michael.limit.management.exception.methodNotValid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class ValidationErrorResponse {

    private HttpStatus httpStatus = HttpStatus.BAD_REQUEST;

    private List<Violation> errors = new ArrayList<>();

    private String time = String.valueOf(LocalDateTime.now());

}
