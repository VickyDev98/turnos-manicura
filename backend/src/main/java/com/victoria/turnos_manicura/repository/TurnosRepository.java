package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TurnosModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// Solo cuentan los turnos activos: un turno cancelado libera el horario
// JpaSpecificationExecutor: el listado paginado combina filtros opcionales (ver TurnosService.paginaPara)
public interface TurnosRepository extends JpaRepository<TurnosModel, Long>, JpaSpecificationExecutor<TurnosModel> {

    // Clientes que alguna vez tuvieron turno (activo o cancelado) con la manicurista
    @Query("select distinct t.idCliente from TurnosModel t where t.idManicurista = :idManicurista")
    List<Integer> findIdsClientesDeManicurista(@Param("idManicurista") int idManicurista);

    // Todos los turnos (activos y cancelados) cuyo día cae en [desde, hasta): base de los reportes
    List<TurnosModel> findByDiaHorarioGreaterThanEqualAndDiaHorarioLessThan(LocalDateTime desde, LocalDateTime hasta);

    // Cancelaciones por cliente: los turnos cancelados son los que quedaron con activo = false
    interface CancelacionesPorCliente {
        Integer getIdCliente();

        Long getCantidad();
    }

    @Query("select t.idCliente as idCliente, count(t) as cantidad from TurnosModel t "
            + "where t.activo = false group by t.idCliente having count(t) > :minimo order by count(t) desc")
    List<CancelacionesPorCliente> clientesConMasCancelacionesQue(@Param("minimo") long minimo);

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
