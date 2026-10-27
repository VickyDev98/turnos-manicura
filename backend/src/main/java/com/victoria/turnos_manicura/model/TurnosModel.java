package com.victoria.turnos_manicura.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;



@Entity
@Table(name ="turnos")
@Getter
@Setter
public class TurnosModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Positive(message = "La manicurista es obligatoria")
    @Column(name = "id_usuario_manicurista", nullable = false)
    private int idManicurista;
    @Positive(message = "El cliente es obligatorio")
    @Column(name = "id_usuario_cliente", nullable = false)
    private int idCliente;
    @Positive(message = "El servicio es obligatorio")
    @Column(name = "id_servicio", nullable = false)
    private int idServicio;
    // Precio con el que se reservó (lo completa el backend según los servicios de la manicurista)
    private Integer precio;
    // Duración con la que se reservó (si falta, rige la del catálogo)
    @JsonFormat(pattern = "HH:mm:ss")
    private java.time.LocalTime duracion;
    @NotNull(message = "El día y horario son obligatorios")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "dia_horario")
    private LocalDateTime diaHorario;
    private boolean activo;
    // Ya se le mandó al cliente el recordatorio de este horario (al reprogramar vuelve a false). Solo lo maneja el backend
    @JsonIgnore
    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado;

    // La manicurista marcó que el cliente no se presentó (solo aplica a turnos activos que ya empezaron).
    // Solo lo maneja el backend: no se acepta desde el body al reservar
    @JsonIgnore
    @Column(name = "no_asistio", nullable = false)
    private boolean noAsistio;

    // PROXIMO, REALIZADO (activo y su horario ya pasó), NO_ASISTIO (activo, pasado y marcado) o CANCELADO.
    // Se calcula, no se guarda
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getEstado() {
        if (!activo) {
            return "CANCELADO";
        }
        if (noAsistio) {
            return "NO_ASISTIO";
        }
        return diaHorario != null && diaHorario.isBefore(LocalDateTime.now()) ? "REALIZADO" : "PROXIMO";
    }
}
