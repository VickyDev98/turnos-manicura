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
    public List<ServiciosModel> listar() {
        authService.requerirSesion();
        return service.listar();
    }

    // Los clientes solo consultan: crean servicios del catálogo las manicuristas y el admin
    @PostMapping
    public ServiciosModel crear(@Valid @RequestBody ServiciosModel servicio) {
        authService.requerirPersonal();
        return service.guardar(servicio);
    }

    // Solo el admin edita o da de baja servicios del catálogo (las manicuristas manejan los suyos en /ofertas)
    @PutMapping("/{id}")
    public ServiciosModel actualizar(@PathVariable Long id,
                                     @Valid @RequestBody ServiciosModel servicio) {
        authService.requerirAdmin();
        return service.actualizar(id, servicio);
    }

    // Qué servicios ofrece cada manicurista y a qué precio
    @GetMapping("/ofertas")
    public List<ServicioManicuristaModel> ofertas() {
        return ofertaService.listarPara(authService.requerirSesion());
    }

    // Lo de abajo lo hace cada manicurista sobre sus propios servicios

    @PostMapping("/ofertas")
    public ServicioManicuristaModel agregarOferta(@Valid @RequestBody OfertaRequest pedido) {
        return ofertaService.agregar(idPropio(), pedido);
    }

    @PutMapping("/ofertas/{id}")
    public ServicioManicuristaModel actualizarOferta(
            @PathVariable Long id,
            @Valid @RequestBody OfertaUpdate cambios) {
        return ofertaService.actualizar(idPropio(), id, cambios);
    }

    private int idPropio() {
        return authService.requerirManicurista().getIdUsuario().intValue();
    }
}
