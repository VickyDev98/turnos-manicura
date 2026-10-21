package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiposUsuarioRepository extends JpaRepository<TiposUsuarioModel, Long>{
    boolean existsByDescripcionIgnoreCase(String descripcion);

    Optional<TiposUsuarioModel> findByDescripcionIgnoreCase(String descripcion);
}
