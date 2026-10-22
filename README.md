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
- **Cancelar y reprogramar** turnos. Un cliente solo puede cancelar con **24 hs de anticipación** (`app.turnos.horas-minimas-cancelacion`, 0 = sin límite); la manicurista y el admin pueden cancelar siempre.
- **Avisos y recordatorios por mail:** al reservar, cancelar o reprogramar un turno se avisa a la otra parte (manicurista o cliente), y el cliente recibe un **recordatorio 24 hs antes**, una sola vez por horario. Los mails salen en segundo plano y, si fallan, no afectan la operación. Si no hay servidor de correo configurado, no se manda nada. Ver [Avisos por mail](#-avisos-por-mail).
- **Administración de usuarios** con baja lógica: un usuario dado de baja no puede iniciar sesión y se le cierran las sesiones abiertas.
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
│       └── test/java/        # Tests unitarios de los servicios (JUnit 5 + Mockito)
├── frontend/                 # index.html, app.js, styles.css + Dockerfile (nginx)
├── scripts/
│   ├── Turnos.sql            # Script único: crea la base y carga datos de prueba
│   └── historial/            # Migraciones viejas (ya unificadas en Turnos.sql)
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

Antes de levantar, copiá `.env.example` como `.env` y poné tu clave de MySQL (es obligatoria). La base se crea y se carga sola con `scripts/Turnos.sql` la primera vez. Para apagar todo: `docker compose down`. Para **borrar también los datos y volver a empezar de cero**: `docker compose down -v`.

La clave de MySQL se define en `.env` (ver `.env.example`).

### 📧 Avisos por mail

Los avisos están **apagados por defecto**: se activan al definir `SMTP_HOST` en el `.env` (ver `.env.example` para el resto de las variables: puerto, usuario, clave, remitente).

Para **probarlos sin mandar mails de verdad** hay una casilla de prueba (Mailpit): poné `SMTP_HOST=mailpit` y `SMTP_PORT=1025` en `.env`, levantá con `docker compose --profile correo up --build` y mirá la bandeja en http://localhost:8025.

El recordatorio se controla en `application.properties` (`app.recordatorios.horas-antes`, `app.recordatorios.intervalo-minutos`). Si ya tenías una base creada antes de esta función, agregale la columna con
`ALTER TABLE turnos ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;` (o recreala con `docker compose down -v`).

### Opción B: sin Docker
Requisitos: JDK 17 y MySQL 8 en el puerto **3308** (o cambiá el puerto en `application.properties`).

1. Ejecutá `scripts/Turnos.sql` en MySQL (⚠️ hace `DROP DATABASE IF EXISTS TURNOS`: borra y recrea la base).
2. Definí la clave de MySQL en la variable de entorno `DB_PASSWORD` (opcionales: `DB_USER`, `DB_URL`) y corré:
   ```bash
   cd backend
   ./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
   ```
3. Abrí `frontend/index.html` en el navegador. La URL de la API se puede cambiar desde el campo **API** de la parte superior.

Iniciá sesión con alguno de los usuarios de la tabla de arriba.

---

## 🧪 Tests

Hay **100 tests unitarios** de la capa de servicios (`AuthService`, `TurnosService`, `DisponibilidadService`, `UsuariosService`, `ServiciosService`), escritos con **JUnit 5 y Mockito**: los repositorios se simulan, así que corren en segundos y sin base de datos. Cubren las reglas de negocio: superposición de turnos, horarios de trabajo, repeticiones de bloqueos, permisos por rol, validaciones de usuarios y sesiones.

```bash
cd backend
./mvnw test -Dtest='*ServiceTest'     # en Windows: mvnw.cmd test -Dtest=*ServiceTest
```

> `TurnosManicuraApplicationTests` levanta toda la aplicación y necesita MySQL corriendo, por eso se excluye en el comando de arriba.

---

## 🌐 Endpoints principales

| Recurso | Endpoints |
|---|---|
| Auth | `POST /auth/login`, `POST /auth/registro`, `POST /auth/logout` |
| Turnos | `GET/POST /turnos`, `GET /turnos/pagina?page=0&size=20&idManicurista=&momento=` (paginado; `momento` = `TODOS`, `PROXIMOS` o `HISTORIAL`), `PUT /turnos/{id}/cancelar`, `PUT /turnos/{id}/reprogramar`, `GET /turnos/agenda` |
| Disponibilidad | `GET /disponibilidad/manicurista/{id}`, `GET/PUT /disponibilidad/franjas`, `GET/POST/PUT/DELETE /disponibilidad/bloqueos` |
| Servicios | `GET/POST/PUT /servicios`, `GET/POST/PUT /servicios/ofertas` |
| Usuarios | `GET/POST /usuarios`, `PUT /usuarios/{id}`, `PUT /usuarios/{id}/activo` |
| Tipos de usuario | `GET/POST /tiposUsuario` |

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
- Agregar tests de integración de los controllers y de la base (hoy hay tests unitarios de la capa de servicios).
- Migraciones de base con **Flyway** en lugar de un script único.
- Mover las credenciales a un gestor de secretos (hoy van por variables de entorno).
- Avisos también por **WhatsApp**, y recordatorios configurables por cliente (hoy son por mail y fijos en 24 hs).

---

## 👩‍💻 Autora

Proyecto personal de **Victoria**, desarrollado para practicar Java y backend.
