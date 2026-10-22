package com.victoria.turnos_manicura.model;

// Qué turnos se piden en el listado paginado, según su momento
public enum FiltroMomento {
    TODOS,      // todos, los más recientes primero
    PROXIMOS,   // activos que todavía no pasaron, el más cercano primero
    HISTORIAL   // los que ya pasaron (realizados) y los cancelados, los más recientes primero
}
