package com.claimswift.reportingservice.repository;

import com.claimswift.reportingservice.entity.ReportEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEventRepository extends JpaRepository<ReportEvent, Long> {
}
