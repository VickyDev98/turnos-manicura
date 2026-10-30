# 💅 Turnos Manicura

Sistema de reserva de turnos para manicuristas: una API REST en **Java / Spring Boot** con base de datos **MySQL** y un frontend web en **HTML + CSS + JavaScript** puro.

> **Por qué existe este proyecto:** lo empecé para **repasar y afianzar conceptos de Java** (programación orientada a objetos, capas, JPA, validaciones, manejo de errores) construyendo algo parecido a un producto real, en lugar de ejercicios sueltos. Por eso el código prioriza que sea claro y esté bien separado por responsabilidades.

---

## 🔑 Usuarios de prueba

La base de datos viene con datos de ejemplo. **Todos los usuarios tienen la misma contraseña: `123456`**

| Rol | Email | Contraseña | Qué puede hacer |
|---|---|---|---|
| **ADMIN** | `admin@mail.com` | `123456` | Ve todos los turnos, crea/edita/da de baja usuarios, administra el catálogo de servicios y los tipos de usuario |
| **MANICURISTA** | `ana.gomez@mail.com` | `123456` | Ve su agenda del día, define su horario de trabajo, bloquea horarios, elige sus servicios con precio y duración propios |
| **CLIENTE** | `maria.perez@mail.com` | `123456` | Reserva turnos, ve su calendario y próximos turnos, cancela y reprograma |

Otras manicuristas de prueba: `lucia.fernandez@mail.com`, `carla.suarez@mail.com`, `valeria.mendez@mail.com`, `sofia.lopez@mail.com`.
Otros clientes: `laura.rodriguez@mail.com`, `paula.martinez@mail.com`, `julieta.sanchez@mail.com`, `camila.diaz@mail.com`.

También podés crear tu propia cuenta de cliente o manicurista desde **"¿No tenés cuenta? Registrate"** en la pantalla de login.

> Son credenciales de **demostración** para una base local. No usar en ningún entorno real.

---

## ✨ Funcionalidades

- **Login y registro** con tres roles (cliente, manicurista, admin). Contraseñas guardadas con **BCrypt** y sesión por token `Bearer`.
- **Reserva de turnos con validaciones de negocio:**
  - no se pueden superponer turnos de la misma manicurista ni del mismo cliente (se calcula con la duración de cada servicio);
  - no se puede reservar en el pasado ni a más de 6 meses;
  - solo se reserva dentro del horario de trabajo de la manicurista y fuera de sus bloqueos.
- **Horarios de trabajo y bloqueos** por manicurista, con repetición `UNICA`, `MES` (todos los mismos días de la semana del mes) o `SIEMPRE`.
- **Servicios por manicurista:** cada una ofrece servicios del catálogo con **su propio precio y duración**. El turno guarda el precio y la duración con los que se reservó, así cambios posteriores no alteran turnos viejos.
- **Agenda visual del día** (ocupado / libre) y **calendario** para el cliente.
- **Estado e historial de turnos:** cada turno es `PROXIMO`, `REALIZADO` (activo y su horario ya pasó) o `CANCELADO`, calculado solo. La tabla de turnos tiene un selector Todos / Próximos / Historial, para todos los roles.
- **Cancelar y reprogramar** turnos. Un cliente solo puede cancelar o reprogramar con **24 hs de anticipación** respecto del horario actual del turno (`app.turnos.horas-minimas-cancelacion`, 0 = sin límite); la manicurista y el admin pueden siempre.
- **Asistencia:** la manicurista (o el admin) puede marcar "No asistió" en un turno que ya empezó, y corregirlo si fue un error. El estado del turno pasa a `NO_ASISTIO` y queda en el historial.
- **Avisos y recordatorios por mail:** al reservar, cancelar o reprogramar un turno se avisa a la otra parte (manicurista o cliente), y el cliente recibe un **recordatorio 24 hs antes**, una sola vez por horario. Los mails salen en segundo plano y, si fallan, no afectan la operación. Si no hay servidor de correo configurado, no se manda nada. Ver [Avisos por mail](#-avisos-por-mail).
- **Reportes del admin** (pestaña "Reportes"): turnos asignados por servicio, turnos cancelados por día o por semana y clientes con más de 3 cancelaciones, para el período que se elija. Los turnos se ubican por el día en que eran (no se guarda cuándo se canceló).
- **Detalle del turno:** fecha, horario, servicio y precio, manicurista, dirección de la sede y estado, con las acciones del turno (reprogramar, cancelar con confirmación).
- **Modo claro y oscuro** (sigue el del sistema y se puede cambiar con el botón; se recuerda), indicador de carga y accesibilidad: contraste AA, foco visible, navegación por teclado, lectores de pantalla (pestañas, avisos y errores de formularios).
- **Administración de usuarios** con baja lógica: un usuario dado de baja no puede iniciar sesión y pierde el acceso enseguida, aunque su token siga vigente.
- **Manejo centralizado de errores:** los mensajes de validación llegan al frontend y se muestran al usuario.

---

## 🧱 Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.2.5 (Web, Data JPA, Validation, Security) |
| Base de datos | MySQL 8 |
| Utilidades | Lombok, BCrypt |
| Build | Maven (con wrapper `mvnw`) |
| Contenedores | Docker y Docker Compose (nginx para el frontend) |
| Frontend | HTML, CSS y JavaScript vanilla (sin frameworks) |

---

## 🗂️ Estructura

```
Proyecto 2026/
├── backend/                  # API REST (Spring Boot)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/victoria/turnos_manicura/
│       │   ├── controller/   # Endpoints REST
│       │   ├── service/      # Reglas de negocio
│       │   ├── repository/   # Acceso a datos (Spring Data JPA)
│       │   ├── model/        # Entidades JPA
│       │   ├── dto/          # Objetos de entrada/salida de la API
│       │   ├── exception/    # Validaciones y manejo global de errores
│       │   └── config/       # Seguridad y CORS
│       ├── main/resources/db/migration/     # Migraciones de Flyway (esquema): V1__..., V2__...
│       ├── main/resources/db/datos-prueba/  # Datos de prueba, solo para desarrollo
│       └── test/java/        # Tests unitarios de los servicios (JUnit 5 + Mockito)
├── frontend/                 # index.html, app.js, styles.css + Dockerfile (nginx)
├── scripts/
│   └── historial/            # Scripts viejos (anteriores a Flyway, solo de consulta)
├── docker-compose.yml        # Levanta MySQL + backend + frontend
└── .env.example              # Variables de entorno de ejemplo
```

**Por qué está organizado así:** cada pieza del sistema (base de datos, backend, frontend) vive en su propia carpeta, así se puede entender, cambiar o desplegar por separado. El `docker-compose.yml` está en la raíz porque coordina las tres. Los tests de Java se quedan en `backend/src/test` porque es donde Maven los espera. La configuración que cambia según el entorno (URL y clave de la base) se pasa por **variables de entorno** en lugar de escribirse en el código.

El backend sigue una arquitectura en capas: **Controller → Service → Repository → Base de datos**, con DTOs para no exponer las entidades directamente.

---

## 🚀 Cómo ejecutarlo

### Opción A: con Docker (recomendada)
Solo necesitás [Docker](https://www.docker.com/products/docker-desktop/). Desde la raíz del proyecto:

```bash
docker compose up --build
```

La primera vez tarda unos minutos (descarga imágenes y compila el backend). Cuando termina:

| Servicio | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend (API) | http://localhost:8080 |
| MySQL | `localhost:3309` (usuario `root`, clave la de tu `.env`) |

Antes de levantar, copiá `.env.example` como `.env` y poné tu clave de MySQL y una `JWT_SECRET` (la clave que firma los tokens de sesión, mínimo 32 caracteres); las dos son obligatorias. El backend crea las tablas y carga los datos de prueba solo, con **Flyway**, la primera vez que arranca (ver "Cambios en la base de datos" más abajo). Si ya tenías una base creada con el sistema anterior (`Turnos.sql`), recreala con `docker compose down -v`. Para apagar todo: `docker compose down`. Para **borrar también los datos y volver a empezar de cero**: `docker compose down -v`.

La clave de MySQL se define en `.env` (ver `.env.example`).

### 📧 Avisos por mail

Los avisos están **apagados por defecto**: se activan al definir `SMTP_HOST` en el `.env` (ver `.env.example` para el resto de las variables: puerto, usuario, clave, remitente).

Para **probarlos sin mandar mails de verdad** hay una casilla de prueba (Mailpit): poné `SMTP_HOST=mailpit` y `SMTP_PORT=1025` en `.env`, levantá con `docker compose --profile correo up --build` y mirá la bandeja en http://localhost:8025.

El recordatorio se controla en `application.properties` (`app.recordatorios.horas-antes`, `app.recordatorios.intervalo-minutos`).

### 💬 Avisos por WhatsApp (manuales y gratis)

En la tabla de turnos, la manicurista (y el admin) ve links para avisar al cliente: **Confirmar** y **Recordar** (con el logo de WhatsApp) en los turnos próximos, y **Avisar cancelación** en los cancelados. Cada link abre WhatsApp con el mensaje ya escrito y la persona lo envía; usa `wa.me`, así que no hay API ni costo. Solo aparecen si el cliente cargó su teléfono, y la manicurista ve el teléfono únicamente de los clientes que tuvieron turno con ella.

El teléfono se guarda como texto, solo dígitos con código de área (10 a 15; con 10 se asume un número argentino y se le antepone `549`). El envío automático por la API oficial de WhatsApp tiene costo y queda como mejora.

### 🗄️ Cambios en la base de datos (Flyway)

El esquema se maneja con **migraciones**: archivos SQL numerados en `backend/src/main/resources/db/migration/` (`V1__esquema_inicial.sql`, `V2__...`). Al arrancar, el backend aplica solo las que la base todavía no tiene (las anota en la tabla `flyway_schema_history`).

- **Para cambiar el esquema** (por ejemplo, agregar una columna): crear un archivo nuevo con el número siguiente, como `V2__agrega_columna_x.sql`, y escribir ahí el `ALTER TABLE`. **Nunca editar una migración ya aplicada**: Flyway lo detecta y el backend no arranca.
- **Datos de prueba** (usuarios con clave `123456`, turnos de ejemplo): están aparte, en `db/datos-prueba/`, y solo se cargan si se agrega esa carpeta con la variable `FLYWAY_LOCATIONS`. Docker Compose ya lo hace; sin esa variable solo se aplica el esquema.

### Opción B: sin Docker
Requisitos: JDK 17 y MySQL 8 en el puerto **3308** (o cambiá el puerto en `application.properties`).

1. Creá la base vacía en MySQL: `CREATE DATABASE TURNOS;` (las tablas las crea Flyway al arrancar el backend).
2. Definí la clave de MySQL en la variable de entorno `DB_PASSWORD` (opcionales: `DB_USER`, `DB_URL`) y la clave de los tokens en `JWT_SECRET` (mínimo 32 caracteres). Para cargar también los datos de prueba, definí `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/datos-prueba`. Después corré:
   ```bash
   cd backend
   ./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
   ```
3. Abrí `frontend/index.html` en el navegador. La URL de la API se puede cambiar desde el campo **API** de la parte superior.

Iniciá sesión con alguno de los usuarios de la tabla de arriba.

---

## 🧪 Tests

Hay **141 tests unitarios** de la capa de servicios (`AuthService`, `TurnosService`, `DisponibilidadService`, `UsuariosService`, `ServiciosService`, `NotificacionesService`, `RecordatoriosService`), escritos con **JUnit 5 y Mockito**: los repositorios se simulan, así que corren en segundos y sin base de datos. Cubren las reglas de negocio: superposición de turnos, horarios de trabajo, repeticiones de bloqueos, permisos por rol, validaciones de usuarios y sesiones.

Además hay **tests de integración** (`src/test/.../integration/`) que levantan la aplicación completa (filtro de sesión, controllers, services y repositorios) y le mandan pedidos HTTP simulados con **MockMvc**. Usan una base **H2 en memoria**, así que tampoco necesitan MySQL ni Docker. Cubren login, registro, bloqueo por intentos fallidos, rutas protegidas por rol, el listado paginado de turnos (por rol y por momento) y la cancelación con anticipación.

```bash
cd backend
./mvnw test                           # todos los tests; en Windows: mvnw.cmd test
./mvnw test -Dtest='*ServiceTest'     # solo los unitarios
```

> H2 es otra base distinta de MySQL: el esquema de los tests sale de las entidades, no de las migraciones de Flyway (que acá están apagadas), así que estos tests no detectan diferencias entre ambos.

---

## 🌐 Endpoints principales

| Recurso | Endpoints |
|---|---|
| Auth | `POST /auth/login`, `POST /auth/registro`, `POST /auth/logout` |
| Turnos | `GET/POST /turnos`, `GET /turnos/pagina?page=0&size=20&idManicurista=&momento=` (paginado; `momento` = `TODOS`, `PROXIMOS` o `HISTORIAL`), `PUT /turnos/{id}/cancelar`, `PUT /turnos/{id}/reprogramar`, `PUT /turnos/{id}/asistencia` (la manicurista del turno o el admin marcan `{"noAsistio": true}` o lo corrigen con `false`), `GET /turnos/agenda` |
| Disponibilidad | `GET /disponibilidad/manicurista/{id}`, `GET/PUT /disponibilidad/franjas`, `GET/POST/PUT/DELETE /disponibilidad/bloqueos` |
| Servicios | `GET/POST/PUT /servicios`, `GET/POST/PUT /servicios/ofertas` |
| Usuarios | `GET/POST /usuarios`, `PUT /usuarios/{id}`, `PUT /usuarios/{id}/activo` |
| Tipos de usuario | `GET/POST /tiposUsuario` |
| Reportes (solo admin) | `GET /reportes/turnos?desde=&hasta=` (fechas `yyyy-MM-dd`; sin fechas, el mes actual) |

Las rutas protegidas esperan el header `Authorization: Bearer <token>` que devuelve el login.

---

## 📚 Qué practiqué / aprendí

- Modelado de entidades y relaciones con **JPA/Hibernate** y diseño del esquema en SQL.
- **Arquitectura en capas** y separación de responsabilidades (controller / service / repository / DTO).
- **Reglas de negocio** no triviales: detección de superposición de intervalos de tiempo, repeticiones de bloqueos, validación de fechas.
- **Manejo de excepciones** con un `@ControllerAdvice` y una excepción de validación propia.
- **Seguridad básica:** hash de contraseñas con BCrypt y control de acceso por rol.
- **Baja lógica** en lugar de borrar datos, y snapshot de precio/duración en el turno.
- Consumo de una API REST desde JavaScript sin frameworks.

---

## 🔭 Posibles mejoras

- Reemplazar las sesiones en memoria por **JWT** o Spring Security completo (hoy la autorización se resuelve en los servicios y se pierde al reiniciar el backend).
- Migraciones de base con **Flyway** en lugar de un script único.
- Mover las credenciales a un gestor de secretos (hoy van por variables de entorno).
- Envío **automático** por WhatsApp (API oficial, con costo), y recordatorios configurables por cliente (hoy son por mail y fijos en 24 hs).

---

## 👩‍💻 Autora

Proyecto personal de **Victoria**, desarrollado para practicar Java y backend.
