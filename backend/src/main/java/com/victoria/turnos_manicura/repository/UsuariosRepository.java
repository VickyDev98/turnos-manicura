package com.victoria.turnos_manicura.repository;
import com.victoria.turnos_manicura.model.UsuariosModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface UsuariosRepository extends  JpaRepository<UsuariosModel, Long>{
    boolean existsByEmailIgnoreCase(String email);

    Optional<UsuariosModel> findByEmailIgnoreCase(String email);

    // SELECT ... FOR UPDATE: dentro de una transacción, otra que pida el mismo usuario espera hasta el commit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuariosModel u where u.id = :id")
    Optional<UsuariosModel> findByIdForUpdate(@Param("id") Long id);
}
