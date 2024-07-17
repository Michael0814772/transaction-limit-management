package com.michael.limit.management.repository;

import com.michael.limit.management.model.ServiceTypeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceTypeModel, Long> {

    @Query(value = "select s from ServiceTypeModel s where s.serviceTypeId = :typeId")
    Optional<ServiceTypeModel> findByServiceTypeId(@Param("typeId") int id);
}
