package com.victoria.turnos_manicura.repository;

import com.victoria.turnos_manicura.model.TokenRenovacionModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokensRenovacionRepository extends JpaRepository<TokenRenovacionModel, Long> {

    // SELECT ... FOR UPDATE: si llegan dos renovaciones con el mismo token, la segunda espera a la primera
    // y ya lo encuentra usado (si no, las dos podrían renovar con el mismo token)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRenovacionModel t where t.hashToken = :hash")
    Optional<TokenRenovacionModel> findByHashTokenForUpdate(@Param("hash") String hash);

    void deleteByHashToken(String hashToken);

    void deleteByIdUsuario(int idUsuario);

    // Limpieza: los vencidos ya no sirven para nada
    void deleteByIdUsuarioAndVenceBefore(int idUsuario, LocalDateTime fecha);
}
