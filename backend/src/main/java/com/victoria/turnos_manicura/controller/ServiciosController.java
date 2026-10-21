package com.victoria.turnos_manicura.controller;


import com.victoria.turnos_manicura.dto.OfertaRequest;
import com.victoria.turnos_manicura.dto.OfertaUpdate;
import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.OfertaService;
import com.victoria.turnos_manicura.service.ServiciosService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/servicios")
public class ServiciosController {

    private final ServiciosService service;
    private final OfertaService ofertaService;
    private final AuthService authService;

    public ServiciosController(ServiciosService service, OfertaService ofertaService, AuthService authService) {
        this.service = service;
        this.ofertaService = ofertaService;
        this.authService = authService;
    }

    // Catálogo general de servicios
    @GetMapping
    public List<ServiciosModel> listar(@RequestHeader(value = "Authorization", required = false) String auth) {
        authService.requerirSesion(auth);
        return service.listar();
    }

    // Los clientes solo consultan: crean servicios del catálogo las manicuristas y el admin
    @PostMapping
    public ServiciosModel crear(@RequestHeader(value = "Authorization", required = false) String auth,
                                @Valid @RequestBody ServiciosModel servicio) {
        authService.requerirPersonal(auth);
        return service.guardar(servicio);
    }

    // Solo el admin edita o da de baja servicios del catálogo (las manicuristas manejan los suyos en /ofertas)
    @PutMapping("/{id}")
    public ServiciosModel actualizar(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @PathVariable Long id,
                                     @Valid @RequestBody ServiciosModel servicio) {
        authService.requerirAdmin(auth);
        return service.actualizar(id, servicio);
    }

    // Qué servicios ofrece cada manicurista y a qué precio
    @GetMapping("/ofertas")
    public List<ServicioManicuristaModel> ofertas(@RequestHeader(value = "Authorization", required = false) String auth) {
        return ofertaService.listarPara(authService.requerirSesion(auth));
    }

    // Lo de abajo lo hace cada manicurista sobre sus propios servicios

    @PostMapping("/ofertas")
    public ServicioManicuristaModel agregarOferta(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody OfertaRequest pedido) {
        return ofertaService.agregar(idPropio(auth), pedido);
    }

    @PutMapping("/ofertas/{id}")
    public ServicioManicuristaModel actualizarOferta(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @Valid @RequestBody OfertaUpdate cambios) {
        return ofertaService.actualizar(idPropio(auth), id, cambios);
    }

    private int idPropio(String authorization) {
        return authService.requerirManicurista(authorization).getIdUsuario().intValue();
    }
}
