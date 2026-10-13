package com.example.appointment.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BlockedPeriodRepository extends JpaRepository<BlockedPeriod, Long> {
    @Query("""
            SELECT b
            FROM BlockedPeriod b
            WHERE b.employeeId = :employeeId
              AND b.startAt < :rangeEnd
              AND b.endAt > :rangeStart
            ORDER BY b.startAt ASC
            """)
    List<BlockedPeriod> findOverlappingByEmployeeId(
            @Param("employeeId") Long employeeId,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd);
}
