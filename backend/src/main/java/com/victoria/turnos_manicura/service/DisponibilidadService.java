package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.BloqueoRequest;
import com.victoria.turnos_manicura.dto.FranjaRequest;
import com.victoria.turnos_manicura.dto.Intervalo;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.BloqueoModel;
import com.victoria.turnos_manicura.model.FranjaTrabajoModel;
import com.victoria.turnos_manicura.model.Repeticion;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.repository.BloqueosRepository;
import com.victoria.turnos_manicura.repository.FranjasTrabajoRepository;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Horario de trabajo (franjas por día de la semana) y bloqueos de las manicuristas.
 * Si una manicurista no cargó ninguna franja, atiende todos los días en el horario de apertura.
 */
@Service
public class DisponibilidadService {

    private static final String FUERA_DE_HORARIO = "Fuera de horario";
    private static final DateTimeFormatter DIA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    private final FranjasTrabajoRepository franjasRepository;
    private final BloqueosRepository bloqueosRepository;
    private final TurnosRepository turnosRepository;
    private final ServiciosRepository serviciosRepository;

    public DisponibilidadService(FranjasTrabajoRepository franjasRepository, BloqueosRepository bloqueosRepository,
                                 TurnosRepository turnosRepository, ServiciosRepository serviciosRepository) {
        this.franjasRepository = franjasRepository;
        this.bloqueosRepository = bloqueosRepository;
        this.turnosRepository = turnosRepository;
        this.serviciosRepository = serviciosRepository;
    }

    // ---------- Consulta ----------

    // Tramos del día (entre apertura y cierre) en los que NO atiende, ordenados por hora
    public List<Intervalo> noDisponible(int idManicurista, LocalDate fecha) {
        return noDisponible(fecha, franjasRepository.findByIdManicurista(idManicurista),
                bloqueosRepository.findByIdManicurista(idManicurista));
    }

    // Tramos del día tomados por turnos reservados (sin datos de los clientes): lo ven los clientes al elegir horario
    public List<Intervalo> ocupadoPorTurnos(int idManicurista, LocalDate fecha, Long excluirTurno) {
        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();
        List<Intervalo> resultado = new ArrayList<>();

        for (TurnosModel t : turnosRepository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(
                idManicurista, inicioDia.minusHours(8), finDia)) {
            if (excluirTurno != null && excluirTurno.equals(t.getId())) {
                continue;
            }
            ServiciosModel servicio = serviciosRepository.findById((long) t.getIdServicio()).orElse(null);
            LocalDateTime inicio = t.getDiaHorario();
            LocalDateTime fin = TurnosService.finDe(t, servicio);
            if (!inicio.isBefore(finDia) || !fin.isAfter(inicioDia)) {
                continue;
            }
            LocalTime desde = inicio.isBefore(inicioDia) ? LocalTime.MIDNIGHT : inicio.toLocalTime();
            LocalTime hasta = fin.isBefore(finDia) ? fin.toLocalTime() : LocalTime.of(23, 59);
            resultado.add(new Intervalo(desde, hasta, null, null));
        }
        return resultado;
    }

    // Se usa al reservar: el turno [inicio, fin] no puede tocar ningún tramo no disponible
    public void validarTurno(int idManicurista, LocalDateTime inicio, LocalDateTime fin) {
        LocalTime desde = inicio.toLocalTime();
        LocalTime hasta = fin.toLocalTime();
        for (Intervalo i : noDisponible(idManicurista, inicio.toLocalDate())) {
            if (desde.isBefore(i.getHasta()) && hasta.isAfter(i.getDesde())) {
                throw new ValidacionException("diaHorario", "La manicurista no atiende en ese horario");
            }
        }
    }

    public List<FranjaTrabajoModel> listarFranjas(int idManicurista) {
        return franjasRepository.findByIdManicurista(idManicurista).stream()
                .sorted(Comparator.comparing(FranjaTrabajoModel::getDiaSemana)
                        .thenComparing(FranjaTrabajoModel::getHoraDesde))
                .collect(Collectors.toList());
    }

    // Solo los bloqueos que todavía pueden afectar: los de días pasados ya no importan
    public List<BloqueoModel> listarBloqueos(int idManicurista) {
        LocalDate hoy = LocalDate.now();
        return bloqueosRepository.findByIdManicurista(idManicurista).stream()
                .filter(b -> {
                    if (b.getRepeticion() == Repeticion.UNICA) return !b.getFecha().isBefore(hoy);
                    if (b.getRepeticion() == Repeticion.MES) {
                        return !YearMonth.from(b.getFecha()).isBefore(YearMonth.from(hoy));
                    }
                    return true;
                })
                .sorted(Comparator.comparing(BloqueoModel::getFecha).thenComparing(BloqueoModel::getHoraDesde))
                .collect(Collectors.toList());
    }

    // ---------- Horario de trabajo ----------

    // Reemplaza todo el horario semanal. Una lista vacía vuelve al horario completo
    @Transactional
    public List<FranjaTrabajoModel> guardarFranjas(int idManicurista, List<FranjaRequest> pedidas) {
        List<FranjaTrabajoModel> franjas = new ArrayList<>();
        for (FranjaRequest p : pedidas) {
            validarRango(p.getHoraDesde(), p.getHoraHasta(), "franjas");
            FranjaTrabajoModel f = new FranjaTrabajoModel();
            f.setIdManicurista(idManicurista);
            f.setDiaSemana(p.getDiaSemana());
            f.setHoraDesde(p.getHoraDesde());
            f.setHoraHasta(p.getHoraHasta());
            franjas.add(f);
        }

        for (int i = 0; i < franjas.size(); i++) {
            for (int j = i + 1; j < franjas.size(); j++) {
                FranjaTrabajoModel a = franjas.get(i);
                FranjaTrabajoModel b = franjas.get(j);
                if (a.getDiaSemana() == b.getDiaSemana()
                        && a.getHoraDesde().isBefore(b.getHoraHasta()) && a.getHoraHasta().isAfter(b.getHoraDesde())) {
                    throw new ValidacionException("franjas", "Hay franjas que se superponen el mismo día");
                }
            }
        }

        validarSinTurnos(idManicurista, franjas, bloqueosRepository.findByIdManicurista(idManicurista), "franjas");

        franjasRepository.deleteByIdManicurista(idManicurista);
        franjasRepository.flush();
        return franjasRepository.saveAll(franjas);
    }

    // ---------- Bloqueos ----------

    @Transactional
    public BloqueoModel crearBloqueo(int idManicurista, BloqueoRequest pedido) {
        BloqueoModel bloqueo = new BloqueoModel();
        bloqueo.setIdManicurista(idManicurista);
        return guardarBloqueo(bloqueo, pedido);
    }

    @Transactional
    public BloqueoModel actualizarBloqueo(int idManicurista, Long id, BloqueoRequest pedido) {
        return guardarBloqueo(bloqueoPropio(idManicurista, id), pedido);
    }

    @Transactional
    public void eliminarBloqueo(int idManicurista, Long id) {
        bloqueosRepository.delete(bloqueoPropio(idManicurista, id));
    }

    private BloqueoModel guardarBloqueo(BloqueoModel bloqueo, BloqueoRequest pedido) {
        validarRango(pedido.getHoraDesde(), pedido.getHoraHasta(), "horaDesde");
        if (pedido.getFecha().isBefore(LocalDate.now())) {
            throw new ValidacionException("fecha", "La fecha no puede ser anterior a hoy");
        }

        String motivo = pedido.getMotivo() == null ? "" : pedido.getMotivo().trim();

        // Se arma el bloqueo nuevo sin tocar el original hasta saber que no choca con turnos
        BloqueoModel nuevo = new BloqueoModel();
        nuevo.setId(bloqueo.getId());
        nuevo.setIdManicurista(bloqueo.getIdManicurista());
        nuevo.setRepeticion(pedido.getRepeticion());
        nuevo.setFecha(pedido.getFecha());
        nuevo.setHoraDesde(pedido.getHoraDesde());
        nuevo.setHoraHasta(pedido.getHoraHasta());
        nuevo.setMotivo(motivo.isEmpty() ? null : motivo);

        // No se puede bloquear un horario donde ya hay turnos: primero hay que cancelarlos
        List<BloqueoModel> bloqueos = bloqueosRepository.findByIdManicurista(bloqueo.getIdManicurista()).stream()
                .filter(b -> !b.getId().equals(bloqueo.getId()))
                .collect(Collectors.toList());
        bloqueos.add(nuevo);
        validarSinTurnos(bloqueo.getIdManicurista(),
                franjasRepository.findByIdManicurista(bloqueo.getIdManicurista()), bloqueos, "horaDesde");

        bloqueo.setRepeticion(nuevo.getRepeticion());
        bloqueo.setFecha(nuevo.getFecha());
        bloqueo.setHoraDesde(nuevo.getHoraDesde());
        bloqueo.setHoraHasta(nuevo.getHoraHasta());
        bloqueo.setMotivo(nuevo.getMotivo());
        return bloqueosRepository.save(bloqueo);
    }

    private BloqueoModel bloqueoPropio(int idManicurista, Long id) {
        BloqueoModel bloqueo = bloqueosRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El bloqueo no existe"));
        if (bloqueo.getIdManicurista() != idManicurista) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese bloqueo es de otra manicurista");
        }
        return bloqueo;
    }

    // ---------- Cálculo ----------

    private List<Intervalo> noDisponible(LocalDate fecha, List<FranjaTrabajoModel> franjas, List<BloqueoModel> bloqueos) {
        List<Intervalo> resultado = new ArrayList<>();

        if (!franjas.isEmpty()) {
            int dia = fecha.getDayOfWeek().getValue();
            List<FranjaTrabajoModel> deHoy = franjas.stream()
                    .filter(f -> f.getDiaSemana() == dia)
                    .sorted(Comparator.comparing(FranjaTrabajoModel::getHoraDesde))
                    .collect(Collectors.toList());

            // Lo que queda entre las franjas (y antes/después de ellas) no se atiende
            LocalTime cursor = TurnosService.APERTURA;
            for (FranjaTrabajoModel f : deHoy) {
                if (f.getHoraDesde().isAfter(cursor)) {
                    resultado.add(new Intervalo(cursor, f.getHoraDesde(), FUERA_DE_HORARIO, null));
                }
                if (f.getHoraHasta().isAfter(cursor)) {
                    cursor = f.getHoraHasta();
                }
            }
            if (cursor.isBefore(TurnosService.CIERRE)) {
                resultado.add(new Intervalo(cursor, TurnosService.CIERRE, FUERA_DE_HORARIO, null));
            }
        }

        for (BloqueoModel b : bloqueos) {
            if (aplica(b, fecha)) {
                resultado.add(new Intervalo(b.getHoraDesde(), b.getHoraHasta(),
                        b.getMotivo() == null ? "Bloqueado" : b.getMotivo(), b.getId()));
            }
        }

        resultado.sort(Comparator.comparing(Intervalo::getDesde));
        return resultado;
    }

    // ¿El bloqueo rige en esa fecha según su repetición?
    private boolean aplica(BloqueoModel b, LocalDate fecha) {
        if (b.getRepeticion() == Repeticion.UNICA) {
            return fecha.equals(b.getFecha());
        }
        if (fecha.getDayOfWeek() != b.getFecha().getDayOfWeek()) {
            return false;
        }
        if (b.getRepeticion() == Repeticion.MES) {
            return YearMonth.from(fecha).equals(YearMonth.from(b.getFecha()));
        }
        return !fecha.isBefore(b.getFecha());
    }

    private void validarRango(LocalTime desde, LocalTime hasta, String campo) {
        if (!desde.isBefore(hasta)) {
            throw new ValidacionException(campo, "La hora de inicio tiene que ser anterior a la de fin");
        }
        if (desde.isBefore(TurnosService.APERTURA) || hasta.isAfter(TurnosService.CIERRE)) {
            throw new ValidacionException(campo, "El horario tiene que estar entre las "
                    + TurnosService.APERTURA + " y las " + TurnosService.CIERRE + " hs");
        }
    }

    // Revisa los turnos futuros de la manicurista contra el horario propuesto
    private void validarSinTurnos(int idManicurista, List<FranjaTrabajoModel> franjas, List<BloqueoModel> bloqueos,
                                  String campo) {
        List<TurnosModel> turnos = turnosRepository
                .findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(idManicurista, LocalDateTime.now());
        for (TurnosModel t : turnos) {
            ServiciosModel servicio = serviciosRepository.findById((long) t.getIdServicio()).orElse(null);
            LocalDateTime inicio = t.getDiaHorario();
            LocalDateTime fin = TurnosService.finDe(t, servicio);

            for (Intervalo i : noDisponible(inicio.toLocalDate(), franjas, bloqueos)) {
                if (inicio.toLocalTime().isBefore(i.getHasta()) && fin.toLocalTime().isAfter(i.getDesde())) {
                    throw new ValidacionException(campo, "Ya hay un turno el " + DIA_Y_HORA.format(inicio)
                            + " que quedaría en un horario no disponible: cancelalo primero");
                }
            }
        }
    }
}
