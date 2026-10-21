package com.victoria.turnos_manicura.service;


import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

@Service
public class ServiciosService {
    private final ServiciosRepository repository;

    public ServiciosService(ServiciosRepository repository) {
        this.repository = repository;
    }

    public List<ServiciosModel> listar() {
        return repository.findAll();
    }

    public ServiciosModel guardar(ServiciosModel servicio) {
        servicio.setNombre(servicio.getNombre().trim());
        if (servicio.getDescripcion() != null) {
            servicio.setDescripcion(servicio.getDescripcion().trim());
        }

        if (repository.existsByNombreIgnoreCase(servicio.getNombre())) {
            throw new ValidacionException("nombre", "Ya existe un servicio con ese nombre");
        }
        if (servicio.getDuracion().isBefore(LocalTime.of(0, 5))) {
            throw new ValidacionException("duracion", "La duración mínima es de 5 minutos");
        }
        if (servicio.getDuracion().isAfter(LocalTime.of(8, 0))) {
            throw new ValidacionException("duracion", "La duración máxima es de 8 horas");
        }

        if (servicio.getActivo() == null) {
            servicio.setActivo(true);
        }

        return repository.save(servicio);
    }

    // Edita un servicio del catálogo (solo el admin). El precio y la duración son los sugeridos al agregarlo:
    // no cambian lo que ya ofrece cada manicurista ni los turnos reservados
    public ServiciosModel actualizar(Long id, ServiciosModel datos) {
        ServiciosModel servicio = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El servicio no existe"));

        String nombre = datos.getNombre().trim();
        if (repository.findByNombreIgnoreCase(nombre).filter(otro -> !otro.getId().equals(id)).isPresent()) {
            throw new ValidacionException("nombre", "Ya existe un servicio con ese nombre");
        }
        if (datos.getDuracion().isBefore(LocalTime.of(0, 5))) {
            throw new ValidacionException("duracion", "La duración mínima es de 5 minutos");
        }
        if (datos.getDuracion().isAfter(LocalTime.of(8, 0))) {
            throw new ValidacionException("duracion", "La duración máxima es de 8 horas");
        }

        servicio.setNombre(nombre);
        servicio.setDescripcion(datos.getDescripcion() == null ? null : datos.getDescripcion().trim());
        servicio.setDuracion(datos.getDuracion());
        servicio.setPrecio(datos.getPrecio());
        if (datos.getActivo() != null) {
            servicio.setActivo(datos.getActivo());
        }
        return repository.save(servicio);
    }
}
