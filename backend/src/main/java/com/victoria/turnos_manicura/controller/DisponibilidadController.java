package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.dto.BloqueoRequest;
import com.victoria.turnos_manicura.dto.FranjaRequest;
import com.victoria.turnos_manicura.dto.Intervalo;
import com.victoria.turnos_manicura.model.BloqueoModel;
import com.victoria.turnos_manicura.model.FranjaTrabajoModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.DisponibilidadService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/disponibilidad")
public class DisponibilidadController {

    private final DisponibilidadService service;

    public DisponibilidadController(DisponibilidadService service) {
        this.service = service;
    }

    // Lo usan los clientes para ver horarios libres: bloqueos, fuera de horario y turnos ya reservados
    // (excluirTurno = un turno que se está reprogramando, que no cuenta como ocupado),
    // solo desde/hasta (sin motivos ni datos de otras personas)
    @GetMapping("/manicurista/{idManicurista}")
    public List<Intervalo> noDisponible(
            @PathVariable int idManicurista,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long excluirTurno) {
        List<Intervalo> todos = new ArrayList<>(service.noDisponible(idManicurista, fecha));
        todos.addAll(service.ocupadoPorTurnos(idManicurista, fecha, excluirTurno));
        return todos.stream()
                .map(i -> new Intervalo(i.getDesde(), i.getHasta(), null, null))
                .collect(Collectors.toList());
    }

    // Lo de abajo es de la manicurista que inició sesión: solo puede ver y editar lo suyo

    @PreAuthorize("hasRole('MANICURISTA')")
    @GetMapping("/franjas")
    public List<FranjaTrabajoModel> franjas(AuthService.Sesion sesion) {
        return service.listarFranjas(idDe(sesion));
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @PutMapping("/franjas")
    public List<FranjaTrabajoModel> guardarFranjas(AuthService.Sesion sesion,
                                                     @Valid @RequestBody List<@Valid FranjaRequest> franjas) {
        return service.guardarFranjas(idDe(sesion), franjas);
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @GetMapping("/bloqueos")
    public List<BloqueoModel> bloqueos(AuthService.Sesion sesion) {
        return service.listarBloqueos(idDe(sesion));
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @PostMapping("/bloqueos")
    public BloqueoModel crearBloqueo(AuthService.Sesion sesion, @Valid @RequestBody BloqueoRequest pedido) {
        return service.crearBloqueo(idDe(sesion), pedido);
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @PutMapping("/bloqueos/{id}")
    public BloqueoModel actualizarBloqueo(
            AuthService.Sesion sesion,
            @PathVariable Long id,
            @Valid @RequestBody BloqueoRequest pedido) {
        return service.actualizarBloqueo(idDe(sesion), id, pedido);
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @DeleteMapping("/bloqueos/{id}")
    public void eliminarBloqueo(AuthService.Sesion sesion, @PathVariable Long id) {
        service.eliminarBloqueo(idDe(sesion), id);
    }

    private static int idDe(AuthService.Sesion sesion) {
        return sesion.getIdUsuario().intValue();
    }
}
