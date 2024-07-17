package com.michael.limit.management.repository;

import com.michael.limit.management.model.FailedDailyLimitModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedDailyLimitRepository extends JpaRepository<FailedDailyLimitModel, Long> {
}
