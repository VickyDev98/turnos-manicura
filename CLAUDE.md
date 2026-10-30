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

Stack completo (requiere los archivos de `secrets/`: se crean con `scripts\generar-secretos.ps1`, ver abajo): `docker compose up --build` desde la raíz (frontend :3000 vía nginx, API :8080, MySQL :3309). `docker compose down -v` borra los datos.

Frontend: no hay build ni linter; abrir `frontend/index.html` (la URL de la API se cambia en el campo "API" de la UI).

## Arquitectura

Backend en capas `controller → service → repository → MySQL`, con DTOs en `dto/` y entidades JPA en `model/`. Paquete base `com.victoria.turnos_manicura`.

- **Autenticación y autorización por rol con Spring Security (resource server JWT).** `SecurityConfig` define la cadena: sin sesión de servidor (STATELESS), CSRF off, todo exige token salvo `/auth/login`, `/auth/registro` y `/auth/logout` y los preflight OPTIONS (default-deny: una ruta nueva queda protegida sola; en las rutas públicas se ignora el header Bearer), y 401 en JSON con el mismo formato de `ErrorHandler`. El token es un **JWT** HS256 emitido por `JwtService` (Nimbus; lleva id de usuario, rol y vencimiento; clave `JWT_SECRET` obligatoria de ≥32 caracteres; vigencia `app.jwt.minutos-vigencia`, 480, fija, sin refresh). Cada pedido se valida con firma y vencimiento + `SesionVigenteValidator` (usuario activo y token no cerrado: una baja corta el acceso al instante). Logout anota el id del token en `TokensCerradosService` (memoria, se pierde al reiniciar). El rol se declara en cada endpoint con `@PreAuthorize("hasRole('ADMIN')")` (o `hasAnyRole(...)`; `@EnableMethodSecurity` en `SecurityConfig`, que convierte el claim `rol` del token en `ROLE_<rol>`); sin el rol da 403 en JSON con el mensaje único "No tenés permiso para esta opción". Spring valida el cuerpo (`@Valid`) antes del `@PreAuthorize`, así que con un cuerpo inválido sale 400 antes que 403. Los controllers que necesitan saber quién es el usuario declaran un parámetro `AuthService.Sesion sesion`: lo completa `SesionArgumentResolver` (registrado en `WebConfig`) desde el token ya validado, sin leer el header ni el contexto global. Una ruta que solo exige estar logueado no necesita pedirlo. Una ruta nueva sin `@PreAuthorize` queda abierta a cualquier usuario logueado: acordate de anotarla y de sumarla a `RolesIntegrationTest`. Login: 5 fallos por email bloquean 15 min (429).
- **Reglas de negocio en los services** (`TurnosService`, `DisponibilidadService`, `OfertaService`): superposición de turnos por duración, ventana (no pasado, máx. 6 meses), franjas de trabajo y bloqueos con repetición `UNICA | MES | SIEMPRE` (enum `Repeticion`). El turno guarda precio y duración al reservar (snapshot), tomados de `ServicioManicuristaModel` (oferta por manicurista con precio/duración propios sobre el catálogo `ServiciosModel`).
- **Errores:** los services lanzan `ValidacionException`; `ErrorHandler` la convierte en respuesta con mensaje (`server.error.include-message=always`) que el frontend muestra tal cual.
- **Esquema con Flyway, sin Hibernate DDL** (`ddl-auto=none`): las migraciones están en `backend/src/main/resources/db/migration/` (`V1__esquema_inicial.sql` incluye los tipos de usuario, que la app necesita siempre). Un cambio de entidad requiere una migración **nueva** (`V2__...`); nunca editar una ya aplicada (Flyway valida el checksum). Los datos de prueba están aparte en `db/datos-prueba/V1_1__...` y solo se cargan si `FLYWAY_LOCATIONS` los incluye (docker-compose lo hace); como corren justo después de la V1, solo pueden usar el esquema de la V1. `scripts/historial/` son scripts viejos previos a Flyway, no tocar.
- **Secretos como Docker secrets, no como variables de entorno:** la clave de MySQL, `JWT_SECRET` y la clave SMTP viven en archivos de `secrets/` (git-ignorada; los crea `scripts/generar-secretos.ps1`, que migra desde el `.env` y no pisa lo existente). Compose los monta de solo lectura en `/run/secrets/` con `target` = `DB_PASSWORD`, `JWT_SECRET`, `SMTP_PASSWORD`, y Spring Boot los lee con `spring.config.import=optional:configtree:/run/secrets/` (las `${...}` de `application.properties` no cambian; fuera de Docker siguen valiendo las variables de entorno, que tienen prioridad). MySQL usa `MYSQL_ROOT_PASSWORD_FILE`. Al agregar un secreto nuevo: archivo en `secrets/`, entrada en `secrets:` de compose (top-level y en el servicio) y nunca como `environment:`. La clave root solo se aplica al crear el volumen de MySQL: no la cambies en el archivo con una base existente.
- **Reportes del admin:** `ReportesService` calcula los agregados (por servicio, cancelados por día/semana, clientes con más de 3 cancelaciones) y `GET /reportes/turnos` (solo ADMIN) los devuelve; el frontend solo los dibuja. El turno no guarda cuándo se canceló: se ubica por su `diaHorario`.
- **Frontend:** SPA en un único `frontend/app.js` (~1900 líneas) + `index.html` + `styles.css`; sesión por token Bearer devuelto en el login. Los colores salen de variables CSS (`:root` = claro, `[data-theme="dark"]` = oscuro; un script en el `<head>` aplica el tema antes de pintar): nunca poner colores fijos en el CSS, usar las variables (y mantener contraste AA). Accesibilidad: pestañas con `role="tablist"`, foco visible, `aria-invalid`/`aria-describedby` en errores de formularios, `sr-only` para texto solo de lectores.

## Convenciones

- Nombres de clases: dominio en español + sufijo técnico en inglés (`TurnosService`, `ErrorHandler`, `UsuariosModel`, `TurnosRepository`).
- Tests: JUnit 5 + Mockito sobre services en `backend/src/test/.../service/`; los repositorios se mockean.
- Credenciales de prueba: todos los usuarios con clave `123456` (p. ej. `admin@mail.com`, `ana.gomez@mail.com`, `maria.perez@mail.com`).
