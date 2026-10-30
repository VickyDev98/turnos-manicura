package com.victoria.turnos_manicura.controller;

import com.victoria.turnos_manicura.dto.ReporteTurnos;
import com.victoria.turnos_manicura.service.ReportesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/reportes")
public class ReportesController {

    private final ReportesService service;

    public ReportesController(ReportesService service) {
        this.service = service;
    }

    // Reportes del admin. Sin fechas devuelve el mes actual
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/turnos")
    public ReporteTurnos turnos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.turnos(desde, hasta);
    }
}
