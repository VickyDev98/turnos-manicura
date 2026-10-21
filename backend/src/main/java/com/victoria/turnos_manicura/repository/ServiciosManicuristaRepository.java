package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.ServicioManicuristaModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiciosManicuristaRepository extends JpaRepository<ServicioManicuristaModel, Long> {
    List<ServicioManicuristaModel> findByActivoTrue();

    List<ServicioManicuristaModel> findByIdManicuristaOrActivoTrue(int idManicurista);

    Optional<ServicioManicuristaModel> findByIdManicuristaAndIdServicio(int idManicurista, int idServicio);
}
