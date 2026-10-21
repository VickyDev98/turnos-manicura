package com.victoria.turnos_manicura.repository;
import com.victoria.turnos_manicura.model.UsuariosModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UsuariosRepository extends  JpaRepository<UsuariosModel, Long>{
    boolean existsByEmailIgnoreCase(String email);

    Optional<UsuariosModel> findByEmailIgnoreCase(String email);
}
