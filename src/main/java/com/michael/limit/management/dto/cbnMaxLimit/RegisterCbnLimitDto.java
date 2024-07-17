package com.michael.limit.management.dto.cbnMaxLimit;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@RequiredArgsConstructor
@Validated
public class RegisterCbnLimitDto {

    @Valid
    @JsonInclude(NON_NULL)
    private ArrayList<RegisterCbnLimitList> registerCbnLimit;
}
