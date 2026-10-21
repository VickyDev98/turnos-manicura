package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.AgendaBloque;
import com.victoria.turnos_manicura.dto.Intervalo;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service

public class TurnosService {

    // Horario de atención: los turnos tienen que empezar y terminar dentro de este rango
    public static final LocalTime APERTURA = LocalTime.of(6, 0);
    public static final LocalTime CIERRE = LocalTime.of(23, 0);

    // Duración máxima de un servicio (ver ServiciosService): sirve para acotar la búsqueda de turnos que se pisan
    private static final Duration DURACION_MAXIMA = Duration.ofHours(8);

    private final TurnosRepository repository;
    private final UsuariosRepository usuariosRepository;
    private final TiposUsuarioRepository tiposRepository;
    private final ServiciosRepository serviciosRepository;
    private final DisponibilidadService disponibilidadService;
    private final OfertaService ofertaService;

    public TurnosService(TurnosRepository repository, UsuariosRepository usuariosRepository,
                         TiposUsuarioRepository tiposRepository, ServiciosRepository serviciosRepository,
                         DisponibilidadService disponibilidadService, OfertaService ofertaService) {
        this.ofertaService = ofertaService;
        this.disponibilidadService = disponibilidadService;
        this.repository = repository;
        this.usuariosRepository = usuariosRepository;
        this.tiposRepository = tiposRepository;
        this.serviciosRepository = serviciosRepository;
    }

    // Fin de un turno: usa la duración con la que se reservó; los turnos viejos sin ella usan la del catálogo
    public static LocalDateTime finDe(TurnosModel turno, ServiciosModel catalogo) {
        LocalTime duracion = turno.getDuracion();
        if (duracion == null && catalogo != null) {
            duracion = catalogo.getDuracion();
        }
        if (duracion == null) {
            return turno.getDiaHorario();
        }
        return turno.getDiaHorario().plus(Duration.between(LocalTime.MIDNIGHT, duracion));
    }

    // El admin ve todos los turnos; cada manicurista y cada cliente, solo los suyos
    public List<TurnosModel> listarPara(AuthService.Sesion sesion) {
        List<TurnosModel> todos = repository.findAll();
        if (AuthService.ROL_ADMIN.equals(sesion.getRol())) {
            return todos;
        }
        long id = sesion.getIdUsuario();
        boolean manicurista = AuthService.ROL_MANICURISTA.equals(sesion.getRol());
        return todos.stream()
                .filter(t -> manicurista ? t.getIdManicurista() == id : t.getIdCliente() == id)
                .collect(Collectors.toList());
    }

    public List<TurnosModel> listar() {
        return repository.findAll();
    }

    public TurnosModel guardar(TurnosModel turno) {

        validarUsuario(turno.getIdManicurista(), "MANICURISTA", "idManicurista", "una manicurista");
        validarUsuario(turno.getIdCliente(), "CLIENTE", "idCliente", "un cliente");

        ServiciosModel servicio = serviciosRepository.findById((long) turno.getIdServicio())
                .orElseThrow(() -> new ValidacionException("idServicio", "No existe un servicio con ese ID"));
        if (!Boolean.TRUE.equals(servicio.getActivo())) {
            throw new ValidacionException("idServicio", "El servicio " + servicio.getNombre() + " no está activo");
        }

        // La manicurista tiene que ofrecer el servicio; el turno queda con su precio y su duración
        ServicioManicuristaModel oferta = ofertaService.activaDe(turno.getIdManicurista(), turno.getIdServicio());
        turno.setPrecio(oferta.getPrecio());
        turno.setDuracion(oferta.getDuracion());

        // Se ignoran los segundos para que "10:00:30" choque con "10:00:00"
        turno.setDiaHorario(turno.getDiaHorario().withSecond(0).withNano(0));

        validarHorario(turno, servicio, null);

        turno.setActivo(true);

        return repository.save(turno);
    }

    // Mueve un turno a otro día u horario: misma manicurista, mismo servicio, mismo precio y duración
    public TurnosModel reprogramar(Long id, LocalDateTime nuevoHorario, AuthService.Sesion sesion) {
        TurnosModel turno = turnoPropio(id, sesion, "reprogramar");
        if (!turno.isActivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reprogramar un turno cancelado");
        }
        if (turno.getDiaHorario().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede reprogramar un turno que ya pasó");
        }

        // Si la manicurista se dio de baja no se puede mover; el servicio y el precio se mantienen como se reservaron
        validarUsuario(turno.getIdManicurista(), "MANICURISTA", "idManicurista", "una manicurista");
        ServiciosModel servicio = serviciosRepository.findById((long) turno.getIdServicio()).orElse(null);

        turno.setDiaHorario(nuevoHorario.withSecond(0).withNano(0));
        validarHorario(turno, servicio, turno.getId());

        return repository.save(turno);
    }

    public TurnosModel cancelar(Long id, AuthService.Sesion sesion) {
        TurnosModel turno = turnoPropio(id, sesion, "cancelar");
        if (!turno.isActivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El turno ya estaba cancelado");
        }
        if (turno.getDiaHorario().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cancelar un turno que ya pasó");
        }

        turno.setActivo(false);

        return repository.save(turno);
    }

    // Tramos ocupados de una manicurista en un día, ordenados por hora de inicio
    public List<AgendaBloque> agenda(int idManicurista, LocalDate fecha) {
        LocalDateTime desdeDia = fecha.atStartOfDay();
        LocalDateTime hastaDia = fecha.plusDays(1).atStartOfDay();

        List<AgendaBloque> bloques = repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(
                        idManicurista, desdeDia.minus(DURACION_MAXIMA), hastaDia)
                .stream()
                .map(t -> {
                    ServiciosModel servicio = serviciosRepository.findById((long) t.getIdServicio()).orElse(null);
                    UsuariosModel cliente = usuariosRepository.findById((long) t.getIdCliente()).orElse(null);
                    return new AgendaBloque(
                            t.getId(),
                            t.getDiaHorario(),
                            finDe(t, servicio),
                            cliente == null ? "—" : (cliente.getNombre() + " " + cliente.getApellido()).trim(),
                            servicio == null ? "—" : servicio.getNombre(),
                            "TURNO");
                })
                // Un turno de la noche anterior puede seguir en curso, pero solo importa si toca este día
                .filter(b -> b.getInicio().isBefore(hastaDia) && b.getFin().isAfter(desdeDia))
                .collect(Collectors.toList());

        // Lo que la manicurista no atiende ese día (bloqueos y horas fuera de su horario de trabajo)
        for (Intervalo i : disponibilidadService.noDisponible(idManicurista, fecha)) {
            bloques.add(new AgendaBloque(i.getIdBloqueo(), fecha.atTime(i.getDesde()), fecha.atTime(i.getHasta()),
                    null, i.getMotivo(), "NO_DISPONIBLE"));
        }

        bloques.sort(Comparator.comparing(AgendaBloque::getInicio));
        return bloques;
    }

    // Busca el turno y comprueba que sea de quien lo toca (o del admin)
    private TurnosModel turnoPropio(Long id, AuthService.Sesion sesion, String accion) {
        TurnosModel turno = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El turno no existe"));

        boolean esSuyo = turno.getIdManicurista() == sesion.getIdUsuario() || turno.getIdCliente() == sesion.getIdUsuario();
        if (!esSuyo && !AuthService.ROL_ADMIN.equals(sesion.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No podés " + accion + " un turno de otra persona");
        }
        return turno;
    }

    // Reglas del horario de un turno (nuevo o reprogramado). excluirId = el propio turno al reprogramar
    private void validarHorario(TurnosModel turno, ServiciosModel servicio, Long excluirId) {
        LocalDateTime inicio = turno.getDiaHorario();

        if (inicio.isBefore(LocalDateTime.now())) {
            throw new ValidacionException("diaHorario", "El turno no puede quedar en el pasado");
        }
        if (inicio.isAfter(LocalDateTime.now().plusMonths(6))) {
            throw new ValidacionException("diaHorario", "Solo se pueden reservar turnos hasta 6 meses adelante");
        }

        LocalDateTime fin = finDe(turno, servicio);

        validarHorarioDeAtencion(inicio, fin);
        disponibilidadService.validarTurno(turno.getIdManicurista(), inicio, fin);

        // Dos turnos se pisan si uno empieza antes de que el otro termine
        List<TurnosModel> delDiaManicurista = repository.findByIdManicuristaAndActivoTrueAndDiaHorarioBetween(
                turno.getIdManicurista(), inicio.minus(DURACION_MAXIMA), fin);
        if (hayChoque(delDiaManicurista, inicio, fin, excluirId)) {
            throw new ValidacionException("diaHorario", "La manicurista ya tiene un turno que se superpone con ese horario");
        }

        List<TurnosModel> delDiaCliente = repository.findByIdClienteAndActivoTrueAndDiaHorarioBetween(
                turno.getIdCliente(), inicio.minus(DURACION_MAXIMA), fin);
        if (hayChoque(delDiaCliente, inicio, fin, excluirId)) {
            throw new ValidacionException("diaHorario", "El cliente ya tiene otro turno que se superpone con ese horario");
        }
    }

    private boolean hayChoque(List<TurnosModel> existentes, LocalDateTime inicio, LocalDateTime fin, Long excluirId) {
        return existentes.stream()
                .filter(existente -> excluirId == null || !excluirId.equals(existente.getId()))
                .anyMatch(existente -> {
                    ServiciosModel servicio = serviciosRepository.findById((long) existente.getIdServicio()).orElse(null);
                    LocalDateTime inicioExistente = existente.getDiaHorario();
                    LocalDateTime finExistente = finDe(existente, servicio);
                    return inicio.isBefore(finExistente) && fin.isAfter(inicioExistente);
                });
    }

    private void validarHorarioDeAtencion(LocalDateTime inicio, LocalDateTime fin) {
        boolean mismoDia = inicio.toLocalDate().equals(fin.toLocalDate());
        if (inicio.toLocalTime().isBefore(APERTURA) || !mismoDia || fin.toLocalTime().isAfter(CIERRE)) {
            throw new ValidacionException("diaHorario",
                    "El turno tiene que estar entre las " + APERTURA + " y las " + CIERRE
                            + " hs (el servicio termina a las " + fin.toLocalTime() + ")");
        }
    }

    // Verifica que el usuario exista, esté activo y sea del tipo esperado (MANICURISTA o CLIENTE)
    private void validarUsuario(int idUsuario, String tipoEsperado, String campo, String descripcion) {
        UsuariosModel usuario = usuariosRepository.findById((long) idUsuario)
                .orElseThrow(() -> new ValidacionException(campo, "No existe " + descripcion + " con ese ID"));

        boolean esDelTipo = tiposRepository.findById((long) usuario.getTipo())
                .map(t -> t.getDescripcion().equalsIgnoreCase(tipoEsperado))
                .orElse(false);

        if (!esDelTipo) {
            throw new ValidacionException(campo, usuario.getNombre() + " no es " + descripcion);
        }
        if (!usuario.isActivo()) {
            throw new ValidacionException(campo, usuario.getNombre() + " está dado de baja");
        }
    }
}
