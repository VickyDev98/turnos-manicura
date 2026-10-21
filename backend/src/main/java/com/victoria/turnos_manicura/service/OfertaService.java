package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.OfertaRequest;
import com.victoria.turnos_manicura.dto.OfertaUpdate;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.repository.ServiciosManicuristaRepository;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;

/** Servicios que ofrece cada manicurista, con su propio precio. */
@Service
public class OfertaService {

    private final ServiciosManicuristaRepository repository;
    private final ServiciosRepository serviciosRepository;

    public OfertaService(ServiciosManicuristaRepository repository, ServiciosRepository serviciosRepository) {
        this.repository = repository;
        this.serviciosRepository = serviciosRepository;
    }

    // El admin ve todo; una manicurista ve lo suyo (también lo dado de baja) y lo activo de las demás; los clientes, lo activo
    public List<ServicioManicuristaModel> listarPara(AuthService.Sesion sesion) {
        if (AuthService.ROL_ADMIN.equals(sesion.getRol())) {
            return repository.findAll();
        }
        if (AuthService.ROL_MANICURISTA.equals(sesion.getRol())) {
            return repository.findByIdManicuristaOrActivoTrue(sesion.getIdUsuario().intValue());
        }
        return repository.findByActivoTrue();
    }

    // Si ya lo había ofrecido y lo dio de baja, se reactiva (con el precio nuevo si lo manda)
    public ServicioManicuristaModel agregar(int idManicurista, OfertaRequest pedido) {
        ServiciosModel servicio = serviciosRepository.findById((long) pedido.getIdServicio())
                .orElseThrow(() -> new ValidacionException("idServicio", "El servicio no existe"));
        if (!Boolean.TRUE.equals(servicio.getActivo())) {
            throw new ValidacionException("idServicio", "El servicio " + servicio.getNombre() + " no está activo");
        }

        ServicioManicuristaModel oferta = repository
                .findByIdManicuristaAndIdServicio(idManicurista, pedido.getIdServicio())
                .orElseGet(() -> {
                    ServicioManicuristaModel nueva = new ServicioManicuristaModel();
                    nueva.setIdManicurista(idManicurista);
                    nueva.setIdServicio(pedido.getIdServicio());
                    nueva.setPrecio(servicio.getPrecio());
                    nueva.setDuracion(servicio.getDuracion());
                    return nueva;
                });

        if (pedido.getDuracion() != null) {
            validarDuracion(pedido.getDuracion());
            oferta.setDuracion(pedido.getDuracion());
        }
        if (pedido.getPrecio() != null) {
            oferta.setPrecio(pedido.getPrecio());
        }
        oferta.setActivo(true);
        return repository.save(oferta);
    }

    public ServicioManicuristaModel actualizar(int idManicurista, Long id, OfertaUpdate cambios) {
        ServicioManicuristaModel oferta = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El servicio no existe"));
        if (oferta.getIdManicurista() != idManicurista) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese servicio es de otra manicurista");
        }

        if (cambios.getDuracion() != null) {
            validarDuracion(cambios.getDuracion());
            oferta.setDuracion(cambios.getDuracion());
        }
        if (cambios.getPrecio() != null) {
            oferta.setPrecio(cambios.getPrecio());
        }
        if (cambios.getActivo() != null) {
            oferta.setActivo(cambios.getActivo());
        }
        return repository.save(oferta);
    }

    private void validarDuracion(LocalTime duracion) {
        if (duracion.isBefore(LocalTime.of(0, 5))) {
            throw new ValidacionException("duracion", "La duración mínima es de 5 minutos");
        }
        if (duracion.isAfter(LocalTime.of(8, 0))) {
            throw new ValidacionException("duracion", "La duración máxima es de 8 horas");
        }
    }

    // Para validar un turno: la manicurista tiene que ofrecer ese servicio y no haberlo dado de baja
    public ServicioManicuristaModel activaDe(int idManicurista, int idServicio) {
        return repository.findByIdManicuristaAndIdServicio(idManicurista, idServicio)
                .filter(ServicioManicuristaModel::isActivo)
                .orElseThrow(() -> new ValidacionException("idServicio", "La manicurista no ofrece ese servicio"));
    }
}
