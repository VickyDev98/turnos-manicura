package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.FranjaTrabajoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FranjasTrabajoRepository extends JpaRepository<FranjaTrabajoModel, Long> {
    List<FranjaTrabajoModel> findByIdManicurista(int idManicurista);

    void deleteByIdManicurista(int idManicurista);
}
