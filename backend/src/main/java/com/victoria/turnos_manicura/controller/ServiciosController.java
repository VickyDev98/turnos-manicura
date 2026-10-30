package com.victoria.turnos_manicura.controller;


import com.victoria.turnos_manicura.dto.OfertaRequest;
import com.victoria.turnos_manicura.dto.OfertaUpdate;
import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.service.AuthService;
import com.victoria.turnos_manicura.service.OfertaService;
import com.victoria.turnos_manicura.service.ServiciosService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/servicios")
public class ServiciosController {

    private final ServiciosService service;
    private final OfertaService ofertaService;

    public ServiciosController(ServiciosService service, OfertaService ofertaService) {
        this.service = service;
        this.ofertaService = ofertaService;
    }

    // Catálogo general de servicios
    @GetMapping
    public List<ServiciosModel> listar() {
        return service.listar();
    }

    // Los clientes solo consultan: crean servicios del catálogo las manicuristas y el admin
    @PreAuthorize("hasAnyRole('ADMIN', 'MANICURISTA')")
    @PostMapping
    public ServiciosModel crear(@Valid @RequestBody ServiciosModel servicio) {
        return service.guardar(servicio);
    }

    // Solo el admin edita o da de baja servicios del catálogo (las manicuristas manejan los suyos en /ofertas)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ServiciosModel actualizar(@PathVariable Long id,
                                     @Valid @RequestBody ServiciosModel servicio) {
        return service.actualizar(id, servicio);
    }

    // Qué servicios ofrece cada manicurista y a qué precio
    @GetMapping("/ofertas")
    public List<ServicioManicuristaModel> ofertas(AuthService.Sesion sesion) {
        return ofertaService.listarPara(sesion);
    }

    // Lo de abajo lo hace cada manicurista sobre sus propios servicios

    @PreAuthorize("hasRole('MANICURISTA')")
    @PostMapping("/ofertas")
    public ServicioManicuristaModel agregarOferta(AuthService.Sesion sesion, @Valid @RequestBody OfertaRequest pedido) {
        return ofertaService.agregar(idDe(sesion), pedido);
    }

    @PreAuthorize("hasRole('MANICURISTA')")
    @PutMapping("/ofertas/{id}")
    public ServicioManicuristaModel actualizarOferta(
            AuthService.Sesion sesion,
            @PathVariable Long id,
            @Valid @RequestBody OfertaUpdate cambios) {
        return ofertaService.actualizar(idDe(sesion), id, cambios);
    }

    private static int idDe(AuthService.Sesion sesion) {
        return sesion.getIdUsuario().intValue();
    }
}
