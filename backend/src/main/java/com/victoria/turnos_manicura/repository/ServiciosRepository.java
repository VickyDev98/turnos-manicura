package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.ServiciosModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiciosRepository extends JpaRepository <ServiciosModel, Long> {
    boolean existsByNombreIgnoreCase(String nombre);

    Optional<ServiciosModel> findByNombreIgnoreCase(String nombre);
}
