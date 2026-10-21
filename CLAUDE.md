# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Sistema de turnos para manicuristas: API REST Spring Boot 3.2 / Java 17 + MySQL 8 + frontend vanilla (HTML/CSS/JS, sin build). Proyecto de aprendizaje de Java: priorizar código claro y bien separado por capas. README y mensajes de error están en español.

## Comandos

Backend (desde `backend/`; en Windows `mvnw.cmd`):

```bash
./mvnw spring-boot:run                          # requiere MySQL en localhost:3308 y la variable DB_PASSWORD
./mvnw test -Dtest='*ServiceTest'               # tests unitarios (Mockito, sin DB)
./mvnw test -Dtest=TurnosServiceTest#nombreDelMetodo   # un solo test
```

No correr `./mvnw test` a secas sin MySQL: `TurnosManicuraApplicationTests` levanta el contexto completo y necesita la base.

Stack completo (requiere `.env` con `MYSQL_ROOT_PASSWORD`, ver `.env.example`): `docker compose up --build` desde la raíz (frontend :3000 vía nginx, API :8080, MySQL :3309). `docker compose down -v` borra los datos.

Frontend: no hay build ni linter; abrir `frontend/index.html` (la URL de la API se cambia en el campo "API" de la UI).

## Arquitectura

Backend en capas `controller → service → repository → MySQL`, con DTOs en `dto/` y entidades JPA en `model/`. Paquete base `com.victoria.turnos_manicura`.

- **Auth casera, no Spring Security.** `SecurityConfig` hace `permitAll()` y CSRF off; solo aporta el `BCryptPasswordEncoder`. La sesión se exige en `SesionFilter` para toda la API salvo `/auth/login`, `/auth/registro` y `/auth/logout` (default-deny: una ruta nueva queda protegida sola). El rol se valida en cada controller vía `AuthService` (`requerirAdmin/requerirManicurista/requerirPersonal`). Sesiones: `ConcurrentHashMap` en memoria (token UUID → `Sesion`), se pierden al reiniciar y vencen por inactividad (`app.sesion.minutos-inactividad`, 480). Login: 5 fallos por email bloquean 15 min (429). Dar de baja un usuario llama a `cerrarSesionesDe`.
- **Reglas de negocio en los services** (`TurnosService`, `DisponibilidadService`, `OfertaService`): superposición de turnos por duración, ventana (no pasado, máx. 6 meses), franjas de trabajo y bloqueos con repetición `UNICA | MES | SIEMPRE` (enum `Repeticion`). El turno guarda precio y duración al reservar (snapshot), tomados de `ServicioManicuristaModel` (oferta por manicurista con precio/duración propios sobre el catálogo `ServiciosModel`).
- **Errores:** los services lanzan `ValidacionException`; `ErrorHandler` la convierte en respuesta con mensaje (`server.error.include-message=always`) que el frontend muestra tal cual.
- **Esquema sin Hibernate DDL** (`ddl-auto=none`): la base se define en `scripts/Turnos.sql` (hace `DROP DATABASE IF EXISTS TURNOS` y carga datos de prueba; Docker lo ejecuta en el primer arranque). Un cambio de entidad requiere editar ese script; `scripts/historial/` son migraciones viejas ya unificadas, no tocar.
- **Frontend:** SPA en un único `frontend/app.js` (~1500 líneas) + `index.html` + `styles.css`; sesión por token Bearer devuelto en el login.

## Convenciones

- Nombres de clases: dominio en español + sufijo técnico en inglés (`TurnosService`, `ErrorHandler`, `UsuariosModel`, `TurnosRepository`).
- Tests: JUnit 5 + Mockito sobre services en `backend/src/test/.../service/`; los repositorios se mockean.
- Credenciales de prueba: todos los usuarios con clave `123456` (p. ej. `admin@mail.com`, `ana.gomez@mail.com`, `maria.perez@mail.com`).
