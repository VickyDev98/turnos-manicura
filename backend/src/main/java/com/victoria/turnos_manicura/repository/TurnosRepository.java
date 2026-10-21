package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TurnosModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

// Solo cuentan los turnos activos: un turno cancelado libera el horario
public interface TurnosRepository extends JpaRepository<TurnosModel, Long> {

    // Listados paginados (incluyen los cancelados, para mostrarlos en la tabla)
    Page<TurnosModel> findByIdManicurista(int idManicurista, Pageable pageable);

    Page<TurnosModel> findByIdCliente(int idCliente, Pageable pageable);

    Page<TurnosModel> findByIdClienteAndIdManicurista(int idCliente, int idManicurista, Pageable pageable);

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
