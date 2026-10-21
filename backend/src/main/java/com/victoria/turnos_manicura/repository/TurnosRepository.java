package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TurnosModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

// Solo cuentan los turnos activos: un turno cancelado libera el horario
public interface TurnosRepository extends JpaRepository<TurnosModel, Long> {

    List<TurnosModel> findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(
            int idManicurista,
            LocalDateTime desde,
            LocalDateTime hasta
    );

    List<TurnosModel> findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(
            int idManicurista,
            LocalDateTime desde
    );

    List<TurnosModel> findByIdClienteAndActivoTrueAndDiaHorarioGreaterThanEqual(
            int idCliente,
            LocalDateTime desde
    );

    List<TurnosModel> findByIdClienteAndActivoTrueAndDiaHorarioBetween(
            int idCliente,
            LocalDateTime desde,
            LocalDateTime hasta
    );
}
