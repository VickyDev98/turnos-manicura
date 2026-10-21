package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.BloqueoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloqueosRepository extends JpaRepository<BloqueoModel, Long> {
    List<BloqueoModel> findByIdManicurista(int idManicurista);
}
