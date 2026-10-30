package com.victoria.turnos_manicura.controller;


import com.victoria.turnos_manicura.dto.AgendaBloque;
import com.victoria.turnos_manicura.dto.AgendaDia;
import com.victoria.turnos_manicura.dto.AsistenciaRequest;
import com.victoria.turnos_manicura.dto.ReprogramarRequest;
import com.victoria.turnos_manicura.model.FiltroMomento;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.TurnosService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/turnos")
public class TurnosController {

    private final TurnosService service;

    public TurnosController(TurnosService service) {
        this.service = service;
    }

    @GetMapping
    public List<TurnosModel> listar(AuthService.Sesion sesion) {
        return service.listarPara(sesion);
    }

    // Turnos de a una página (page desde 0). Filtros opcionales: idManicurista y momento
    // (TODOS, PROXIMOS o HISTORIAL: los que ya pasaron y los cancelados)
    @GetMapping("/pagina")
    public Page<TurnosModel> pagina(AuthService.Sesion sesion,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) Integer idManicurista,
                                    @RequestParam(defaultValue = "TODOS") FiltroMomento momento) {
        return service.paginaPara(sesion, idManicurista, momento, page, size);
    }

    @PostMapping
    public TurnosModel crear(AuthService.Sesion sesion, @Valid @RequestBody TurnosModel turno) {
        // Una manicurista solo carga turnos propios
        if (AuthService.ROL_MANICURISTA.equals(sesion.getRol())) {
            turno.setIdManicurista(sesion.getIdUsuario().intValue());
        }
        // Un cliente solo reserva para sí mismo
        if (AuthService.ROL_CLIENTE.equals(sesion.getRol())) {
            turno.setIdCliente(sesion.getIdUsuario().intValue());
        }
        return service.guardar(turno);
    }

    @PutMapping("/{id}/cancelar")
    public TurnosModel cancelar(AuthService.Sesion sesion, @PathVariable Long id) {
        return service.cancelar(id, sesion);
    }

    // Marca (o corrige) que el cliente no se presentó a un turno que ya empezó. Solo manicuristas y admin
    @PreAuthorize("hasAnyRole('ADMIN', 'MANICURISTA')")
    @PutMapping("/{id}/asistencia")
    public TurnosModel asistencia(AuthService.Sesion sesion,
                                  @PathVariable Long id,
                                  @Valid @RequestBody AsistenciaRequest pedido) {
        return service.marcarAsistencia(id, pedido.getNoAsistio(), sesion);
    }

    // Mueve un turno a otro horario (lo puede hacer su cliente, su manicurista o el admin)
    @PutMapping("/{id}/reprogramar")
    public TurnosModel reprogramar(AuthService.Sesion sesion,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReprogramarRequest pedido) {
        return service.reprogramar(id, pedido.getDiaHorario(), sesion);
    }

    // Agenda del día de una manicurista (solo para manicuristas). Sin idManicurista devuelve la propia
    @PreAuthorize("hasRole('MANICURISTA')")
    @GetMapping("/agenda")
    public List<AgendaBloque> agenda(
            AuthService.Sesion sesion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long idManicurista) {
        if (idManicurista != null && !idManicurista.equals(sesion.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo podés ver tu propia agenda");
        }
        long id = sesion.getIdUsuario();
        return service.agenda((int) id, fecha);
    }

    // Agenda de la propia manicurista durante 7 días: "desde" y los 6 siguientes
    @PreAuthorize("hasRole('MANICURISTA')")
    @GetMapping("/agenda/semana")
    public List<AgendaDia> agendaSemana(
            AuthService.Sesion sesion,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        long id = sesion.getIdUsuario();
        return service.agendaSemana((int) id, desde);
    }
}
