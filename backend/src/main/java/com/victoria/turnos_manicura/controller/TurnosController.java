package com.victoria.turnos_manicura.controller;


import com.victoria.turnos_manicura.dto.AgendaBloque;
import com.victoria.turnos_manicura.dto.ReprogramarRequest;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.TurnosService;
import jakarta.validation.Valid;
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
    public List<TurnosModel> listar(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.listarPara(authService.requerirSesion(authorization));
    }

    @PostMapping
    public TurnosModel crear(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @Valid @RequestBody TurnosModel turno) {
        AuthService.Sesion sesion = authService.requerirSesion(authorization);
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
    public TurnosModel cancelar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable Long id) {
        return service.cancelar(id, authService.requerirSesion(authorization));
    }

    // Mueve un turno a otro horario (lo puede hacer su cliente, su manicurista o el admin)
    @PutMapping("/{id}/reprogramar")
    public TurnosModel reprogramar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @PathVariable Long id,
                                   @Valid @RequestBody ReprogramarRequest pedido) {
        return service.reprogramar(id, pedido.getDiaHorario(), authService.requerirSesion(authorization));
    }

    // Agenda del día de una manicurista (solo para manicuristas). Sin idManicurista devuelve la propia
    @GetMapping("/agenda")
    public List<AgendaBloque> agenda(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long idManicurista) {
        AuthService.Sesion sesion = authService.requerirManicurista(authorization);
        if (idManicurista != null && !idManicurista.equals(sesion.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo podés ver tu propia agenda");
        }
        long id = sesion.getIdUsuario();
        return service.agenda((int) id, fecha);
    }
}
