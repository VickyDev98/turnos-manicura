# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Sistema de turnos para manicuristas: API REST Spring Boot 3.2 / Java 17 + MySQL 8 + frontend vanilla (HTML/CSS/JS, sin build). Proyecto de aprendizaje de Java: priorizar código claro y bien separado por capas. README y mensajes de error están en español.

## Comandos

Backend (desde `backend/`; en Windows `mvnw.cmd`):

```bash
./mvnw spring-boot:run                          # requiere MySQL en localhost:3308 y la variable DB_PASSWORD
./mvnw test                                     # todos los tests, sin MySQL (los de integración usan H2 en memoria)
./mvnw test -Dtest='*ServiceTest'               # solo los unitarios (Mockito, rápidos)
./mvnw test -Dtest=TurnosServiceTest#nombreDelMetodo   # un solo test
```

Los tests de integración (`src/test/.../integration/`, heredan de `IntegrationTestBase`) levantan la app completa con `@ActiveProfiles("test")`: H2 en modo MySQL con `ddl-auto=create-drop`, esquema generado desde las entidades (Flyway apagado, no se prueban las migraciones) y `NotificacionesService` simulado. Cada test se deshace con rollback.

Stack completo (requiere `.env` con `MYSQL_ROOT_PASSWORD`, ver `.env.example`): `docker compose up --build` desde la raíz (frontend :3000 vía nginx, API :8080, MySQL :3309). `docker compose down -v` borra los datos.

Frontend: no hay build ni linter; abrir `frontend/index.html` (la URL de la API se cambia en el campo "API" de la UI).

## Arquitectura

Backend en capas `controller → service → repository → MySQL`, con DTOs en `dto/` y entidades JPA en `model/`. Paquete base `com.victoria.turnos_manicura`.

- **Auth casera, no Spring Security.** `SecurityConfig` hace `permitAll()` y CSRF off; solo aporta el `BCryptPasswordEncoder`. La sesión se exige en `SesionFilter` para toda la API salvo `/auth/login`, `/auth/registro` y `/auth/logout` (default-deny: una ruta nueva queda protegida sola). El rol se valida en cada controller vía `AuthService` (`requerirAdmin/requerirManicurista/requerirPersonal`). Sesión = token **JWT** firmado HS256 (`JwtService`, librería Nimbus de Spring Security; lleva id de usuario, rol y vencimiento; clave `JWT_SECRET` obligatoria de ≥32 caracteres, vigencia `app.jwt.minutos-vigencia`, 480, fija sin renovación ni refresh). `AuthService.requerirSesion` lo valida y consulta en cada pedido que el usuario siga activo, así una baja corta el acceso al instante. Logout anota el id del token en una lista en memoria (se pierde al reiniciar). Login: 5 fallos por email bloquean 15 min (429).
- **Reglas de negocio en los services** (`TurnosService`, `DisponibilidadService`, `OfertaService`): superposición de turnos por duración, ventana (no pasado, máx. 6 meses), franjas de trabajo y bloqueos con repetición `UNICA | MES | SIEMPRE` (enum `Repeticion`). El turno guarda precio y duración al reservar (snapshot), tomados de `ServicioManicuristaModel` (oferta por manicurista con precio/duración propios sobre el catálogo `ServiciosModel`).
- **Errores:** los services lanzan `ValidacionException`; `ErrorHandler` la convierte en respuesta con mensaje (`server.error.include-message=always`) que el frontend muestra tal cual.
- **Esquema con Flyway, sin Hibernate DDL** (`ddl-auto=none`): las migraciones están en `backend/src/main/resources/db/migration/` (`V1__esquema_inicial.sql` incluye los tipos de usuario, que la app necesita siempre). Un cambio de entidad requiere una migración **nueva** (`V2__...`); nunca editar una ya aplicada (Flyway valida el checksum). Los datos de prueba están aparte en `db/datos-prueba/V1_1__...` y solo se cargan si `FLYWAY_LOCATIONS` los incluye (docker-compose lo hace); como corren justo después de la V1, solo pueden usar el esquema de la V1. `scripts/historial/` son scripts viejos previos a Flyway, no tocar.
- **Frontend:** SPA en un único `frontend/app.js` (~1500 líneas) + `index.html` + `styles.css`; sesión por token Bearer devuelto en el login.

## Convenciones

- Nombres de clases: dominio en español + sufijo técnico en inglés (`TurnosService`, `ErrorHandler`, `UsuariosModel`, `TurnosRepository`).
- Tests: JUnit 5 + Mockito sobre services en `backend/src/test/.../service/`; los repositorios se mockean.
- Credenciales de prueba: todos los usuarios con clave `123456` (p. ej. `admin@mail.com`, `ana.gomez@mail.com`, `maria.perez@mail.com`).
