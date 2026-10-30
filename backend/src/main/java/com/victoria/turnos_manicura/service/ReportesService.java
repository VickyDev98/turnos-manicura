package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.ClienteCancelaciones;
import com.victoria.turnos_manicura.dto.ConteoPeriodo;
import com.victoria.turnos_manicura.dto.ConteoServicio;
import com.victoria.turnos_manicura.dto.ReporteTurnos;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportesService {

    // Se marca a un cliente cuando supera este número de cancelaciones (más de 3)
    public static final int LIMITE_CANCELACIONES = 3;

    // Rango máximo que se puede consultar, para que el reporte no devuelva miles de filas
    private static final long DIAS_MAXIMOS = 366;

    private final TurnosRepository turnosRepository;
    private final ServiciosRepository serviciosRepository;
    private final UsuariosRepository usuariosRepository;

    public ReportesService(TurnosRepository turnosRepository,
                           ServiciosRepository serviciosRepository,
                           UsuariosRepository usuariosRepository) {
        this.turnosRepository = turnosRepository;
        this.serviciosRepository = serviciosRepository;
        this.usuariosRepository = usuariosRepository;
    }

    // Sin fechas se usa el mes actual
    @Transactional(readOnly = true)
    public ReporteTurnos turnos(LocalDate desde, LocalDate hasta) {
        if (desde == null) {
            desde = YearMonth.now().atDay(1);
        }
        if (hasta == null) {
            hasta = YearMonth.from(desde).atEndOfMonth();
        }
        if (hasta.isBefore(desde)) {
            throw new ValidacionException("hasta", "La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= DIAS_MAXIMOS) {
            throw new ValidacionException("hasta", "El rango no puede superar un año");
        }

        List<TurnosModel> turnos = turnosRepository.findByDiaHorarioGreaterThanEqualAndDiaHorarioLessThan(
                desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        List<TurnosModel> asignados = turnos.stream().filter(TurnosModel::isActivo).toList();
        List<TurnosModel> cancelados = turnos.stream().filter(t -> !t.isActivo()).toList();

        return new ReporteTurnos(
                desde,
                hasta,
                asignados.size(),
                cancelados.size(),
                porServicio(asignados),
                cancelacionesPorDia(cancelados, desde, hasta),
                cancelacionesPorSemana(cancelados, desde, hasta),
                LIMITE_CANCELACIONES,
                clientesConCancelaciones());
    }

    private List<ConteoServicio> porServicio(List<TurnosModel> asignados) {
        Map<Long, String> nombres = serviciosRepository.findAll().stream()
                .collect(Collectors.toMap(ServiciosModel::getId, ServiciosModel::getNombre));
        Map<Integer, Long> cantidades = asignados.stream()
                .collect(Collectors.groupingBy(TurnosModel::getIdServicio, Collectors.counting()));

        return cantidades.entrySet().stream()
                .map(e -> new ConteoServicio(
                        nombres.getOrDefault(e.getKey().longValue(), "Servicio #" + e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(ConteoServicio::getCantidad).reversed()
                        .thenComparing(ConteoServicio::getServicio))
                .toList();
    }

    // Un renglón por cada día del rango, también los que no tuvieron cancelaciones (así el gráfico no tiene huecos)
    private List<ConteoPeriodo> cancelacionesPorDia(List<TurnosModel> cancelados, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Long> porDia = cancelados.stream()
                .collect(Collectors.groupingBy(t -> t.getDiaHorario().toLocalDate(), Collectors.counting()));

        List<ConteoPeriodo> resultado = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            resultado.add(new ConteoPeriodo(dia, porDia.getOrDefault(dia, 0L)));
        }
        return resultado;
    }

    // Semanas de lunes a domingo, identificadas por su lunes
    private List<ConteoPeriodo> cancelacionesPorSemana(List<TurnosModel> cancelados, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Long> porSemana = cancelados.stream()
                .collect(Collectors.groupingBy(t -> lunesDe(t.getDiaHorario().toLocalDate()), Collectors.counting()));

        List<ConteoPeriodo> resultado = new ArrayList<>();
        for (LocalDate lunes = lunesDe(desde); !lunes.isAfter(hasta); lunes = lunes.plusWeeks(1)) {
            resultado.add(new ConteoPeriodo(lunes, porSemana.getOrDefault(lunes, 0L)));
        }
        return resultado;
    }

    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private List<ClienteCancelaciones> clientesConCancelaciones() {
        var filas = turnosRepository.clientesConMasCancelacionesQue(LIMITE_CANCELACIONES);
        Map<Long, UsuariosModel> usuarios = usuariosRepository
                .findAllById(filas.stream().map(f -> f.getIdCliente().longValue()).toList()).stream()
                .collect(Collectors.toMap(UsuariosModel::getId, Function.identity()));

        return filas.stream().map(f -> {
            UsuariosModel u = usuarios.get(f.getIdCliente().longValue());
            String nombre = u == null ? "Cliente #" + f.getIdCliente() : (u.getNombre() + " " + u.getApellido()).trim();
            return new ClienteCancelaciones(f.getIdCliente().longValue(), nombre, u == null ? null : u.getEmail(), f.getCantidad());
        }).toList();
    }
}
