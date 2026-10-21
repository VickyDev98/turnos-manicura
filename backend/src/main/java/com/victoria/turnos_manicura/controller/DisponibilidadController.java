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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/disponibilidad")
public class DisponibilidadController {

    private final DisponibilidadService service;
    private final AuthService authService;

    public DisponibilidadController(DisponibilidadService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    // Lo usan los clientes para ver horarios libres: bloqueos, fuera de horario y turnos ya reservados
    // (excluirTurno = un turno que se está reprogramando, que no cuenta como ocupado),
    // solo desde/hasta (sin motivos ni datos de otras personas)
    @GetMapping("/manicurista/{idManicurista}")
    public List<Intervalo> noDisponible(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable int idManicurista,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Long excluirTurno) {
        authService.requerirSesion(auth);
        List<Intervalo> todos = new ArrayList<>(service.noDisponible(idManicurista, fecha));
        todos.addAll(service.ocupadoPorTurnos(idManicurista, fecha, excluirTurno));
        return todos.stream()
                .map(i -> new Intervalo(i.getDesde(), i.getHasta(), null, null))
                .collect(Collectors.toList());
    }

    // Lo de abajo es de la manicurista que inició sesión: solo puede ver y editar lo suyo

    @GetMapping("/franjas")
    public List<FranjaTrabajoModel> franjas(@RequestHeader(value = "Authorization", required = false) String auth) {
        return service.listarFranjas(idPropio(auth));
    }

    @PutMapping("/franjas")
    public List<FranjaTrabajoModel> guardarFranjas(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody List<@Valid FranjaRequest> franjas) {
        return service.guardarFranjas(idPropio(auth), franjas);
    }

    @GetMapping("/bloqueos")
    public List<BloqueoModel> bloqueos(@RequestHeader(value = "Authorization", required = false) String auth) {
        return service.listarBloqueos(idPropio(auth));
    }

    @PostMapping("/bloqueos")
    public BloqueoModel crearBloqueo(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody BloqueoRequest pedido) {
        return service.crearBloqueo(idPropio(auth), pedido);
    }

    @PutMapping("/bloqueos/{id}")
    public BloqueoModel actualizarBloqueo(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @Valid @RequestBody BloqueoRequest pedido) {
        return service.actualizarBloqueo(idPropio(auth), id, pedido);
    }

    @DeleteMapping("/bloqueos/{id}")
    public void eliminarBloqueo(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        service.eliminarBloqueo(idPropio(auth), id);
    }

    private int idPropio(String authorization) {
        return authService.requerirManicurista(authorization).getIdUsuario().intValue();
    }
}
