package com.example.appointment.appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @EntityGraph(attributePaths = {"employee", "customer"})
    List<Appointment> findAllByOrderByStartAtDesc();

    @EntityGraph(attributePaths = {"employee", "customer"})
    List<Appointment> findByStatusOrderByStartAtAsc(AppointmentStatus status);

    @EntityGraph(attributePaths = {"employee", "customer"})
    Optional<Appointment> findDetailedById(Long id);

    @Query("""
            SELECT a
            FROM Appointment a
            WHERE a.employee.id = :employeeId
              AND a.status IN :statuses
              AND a.startAt < :rangeEnd
              AND a.endAt > :rangeStart
            ORDER BY a.startAt ASC
            """)
    List<Appointment> findReservedOverlappingAppointments(
            @Param("employeeId") Long employeeId,
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.employee.id = :employeeId
              AND a.status IN :statuses
              AND a.startAt < :newEndAt
              AND a.endAt > :newStartAt
            """)
    boolean existsOverlappingAppointment(
            @Param("employeeId") Long employeeId,
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("newStartAt") OffsetDateTime newStartAt,
            @Param("newEndAt") OffsetDateTime newEndAt);
}
