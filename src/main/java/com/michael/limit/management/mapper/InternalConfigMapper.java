package com.michael.limit.management.mapper;

import com.michael.limit.management.dto.defaultLimitDto.DefaultLimitRequestDto;
import com.michael.limit.management.model.InternalLimitModel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InternalConfigMapper {

    DefaultLimitRequestDto mapCreateCurrentInternalDto(InternalLimitModel defaultLimitRequestDto);
}
