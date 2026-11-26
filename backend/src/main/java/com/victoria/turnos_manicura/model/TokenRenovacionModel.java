package com.victoria.turnos_manicura.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Un refresh token emitido. Solo se guarda su hash: el token en sí lo tiene únicamente el navegador
@Entity
@Table(name = "tokens_renovacion")
@Getter
@Setter
public class TokenRenovacionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario", nullable = false)
    private int idUsuario;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(nullable = false)
    private LocalDateTime creado;

    @Column(nullable = false)
    private LocalDateTime vence;

    // Ya se cambió por uno nuevo (rotación): presentarlo otra vez es señal de que lo copiaron
    @Column(nullable = false)
    private boolean usado;
}
