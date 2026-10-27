package com.victoria.turnos_manicura.controller;


import com.victoria.turnos_manicura.dto.AgendaBloque;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/turnos")
public class TurnosController {

    private final TurnosService service;
    private final AuthService authService;

    public TurnosController(TurnosService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public List<TurnosModel> listar() {
        return service.listarPara(authService.requerirSesion());
    }

    // Turnos de a una página (page desde 0). Filtros opcionales: idManicurista y momento
    // (TODOS, PROXIMOS o HISTORIAL: los que ya pasaron y los cancelados)
    @GetMapping("/pagina")
    public Page<TurnosModel> pagina(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) Integer idManicurista,
                                    @RequestParam(defaultValue = "TODOS") FiltroMomento momento) {
        return service.paginaPara(authService.requerirSesion(), idManicurista, momento, page, size);
    }

    @PostMapping
    public TurnosModel crear(@Valid @RequestBody TurnosModel turno) {
        AuthService.Sesion sesion = authService.requerirSesion();
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
    public TurnosModel cancelar(@PathVariable Long id) {
        return service.cancelar(id, authService.requerirSesion());
    }

    // Marca (o corrige) que el cliente no se presentó a un turno que ya empezó. Solo manicuristas y admin
    @PutMapping("/{id}/asistencia")
    public TurnosModel asistencia(@PathVariable Long id,
                                  @Valid @RequestBody AsistenciaRequest pedido) {
        return service.marcarAsistencia(id, pedido.getNoAsistio(), authService.requerirPersonal());
    }

    // Mueve un turno a otro horario (lo puede hacer su cliente, su manicurista o el admin)
    @PutMapping("/{id}/reprogramar")
    public TurnosModel reprogramar(@PathVariable Long id,
                                   @Valid @RequestBody ReprogramarRequest pedido) {
        return service.reprogramar(id, pedido.getDiaHorario(), authService.requerirSesion());
    }

    // Agenda del día de una manicurista (solo para manicuristas). Sin idManicurista devuelve la propia
    @GetMapping("/agenda")
    public List<AgendaBloque> agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long idManicurista) {
        AuthService.Sesion sesion = authService.requerirManicurista();
        if (idManicurista != null && !idManicurista.equals(sesion.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo podés ver tu propia agenda");
        }
        long id = sesion.getIdUsuario();
        return service.agenda((int) id, fecha);
    }
}
