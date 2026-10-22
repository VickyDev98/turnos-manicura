package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TurnosModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

// Solo cuentan los turnos activos: un turno cancelado libera el horario
// JpaSpecificationExecutor: el listado paginado combina filtros opcionales (ver TurnosService.paginaPara)
public interface TurnosRepository extends JpaRepository<TurnosModel, Long>, JpaSpecificationExecutor<TurnosModel> {

    // Turnos que todavía no tuvieron su recordatorio
    List<TurnosModel> findByActivoTrueAndRecordatorioEnviadoFalseAndDiaHorarioBetween(
            LocalDateTime desde,
            LocalDateTime hasta
    );

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
