package com.victoria.turnos_manicura.model;

// Cada cuánto se repite un bloqueo, tomando como referencia su fecha
public enum Repeticion {
    UNICA,    // solo ese día
    MES,      // todos los mismos días de la semana de ese mes
    SIEMPRE   // todos los mismos días de la semana desde esa fecha en adelante
}
