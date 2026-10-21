package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public static final String ROL_MANICURISTA = "MANICURISTA";
    public static final String ROL_CLIENTE = "CLIENTE";
    public static final String ROL_ADMIN = "ADMIN";

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(15);

    // Sesión en memoria: se pierde al reiniciar el backend y hay que volver a iniciar sesión.
    // Vence por inactividad: cada uso la renueva (ver requerirSesion)
    @Getter
    public static class Sesion {
        private final Long idUsuario;
        private final String rol;
        private volatile Instant ultimoUso = Instant.now();

        public Sesion(Long idUsuario, String rol) {
            this.idUsuario = idUsuario;
            this.rol = rol;
        }
    }

    // Intentos de login fallidos por email; al llegar al máximo el email queda bloqueado un rato
    private static class Intentos {
        private int fallidos;
        private Instant bloqueadoHasta;
    }

    @Value("${app.sesion.minutos-inactividad:480}")
    private long minutosInactividad = 480;

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();
    private final Map<String, Intentos> intentosLogin = new ConcurrentHashMap<>();
    private final UsuariosRepository usuariosRepository;
    private final TiposUsuarioRepository tiposRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuariosService usuariosService;

    public AuthService(UsuariosRepository usuariosRepository, TiposUsuarioRepository tiposRepository,
                       PasswordEncoder passwordEncoder, UsuariosService usuariosService) {
        this.usuariosService = usuariosService;
        this.usuariosRepository = usuariosRepository;
        this.tiposRepository = tiposRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        String clave = request.getEmail().trim().toLowerCase();
        verificarNoBloqueado(clave);

        UsuariosModel usuario = usuariosRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .filter(u -> u.getPassword() != null && passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .orElseThrow(() -> {
                    registrarFallo(clave);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
                });
        if (!usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tu cuenta está dada de baja");
        }
        intentosLogin.remove(clave);

        String rol = tiposRepository.findById((long) usuario.getTipo())
                .map(t -> t.getDescripcion().toUpperCase())
                .orElse("");

        sesiones.values().removeIf(this::vencida);
        String token = UUID.randomUUID().toString();
        sesiones.put(token, new Sesion(usuario.getId(), rol));

        return new LoginResponse(token, usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getEmail(), rol);
    }

    // Alta desde la pantalla de login: solo CLIENTE o MANICURISTA (ADMIN lo crea otro admin) y deja la sesión iniciada
    public LoginResponse registrar(RegistroRequest request) {
        String rol = request.getRol().trim().toUpperCase();
        if (!ROL_CLIENTE.equals(rol) && !ROL_MANICURISTA.equals(rol)) {
            throw new ValidacionException("rol", "Elegí si sos cliente o manicurista");
        }
        Long idTipo = tiposRepository.findByDescripcionIgnoreCase(rol)
                .orElseThrow(() -> new ValidacionException("rol", "El tipo de usuario no existe"))
                .getId();

        UsuariosModel usuario = new UsuariosModel();
        usuario.setTipo(idTipo.intValue());
        usuario.setNombre(request.getNombre());
        usuario.setApellido(request.getApellido());
        usuario.setNumeroContacto(request.getNumeroContacto());
        usuario.setEmail(request.getEmail());
        usuario.setPassword(request.getPassword());
        // La dirección es de quien atiende: a los clientes no se les guarda
        usuario.setDireccion(ROL_MANICURISTA.equals(rol) ? request.getDireccion() : null);
        usuariosService.guardar(usuario);

        LoginRequest login = new LoginRequest();
        login.setEmail(request.getEmail());
        login.setPassword(request.getPassword());
        return login(login);
    }

    // Al dar de baja a un usuario se le cierran las sesiones abiertas
    public void cerrarSesionesDe(Long idUsuario) {
        sesiones.values().removeIf(s -> s.getIdUsuario().equals(idUsuario));
    }

    public void logout(String authorization) {
        sesiones.remove(token(authorization));
    }

    // Recibe el header "Authorization: Bearer <token>"
    public Sesion requerirSesion(String authorization) {
        String token = token(authorization);
        Sesion sesion = sesiones.get(token);
        if (sesion != null && vencida(sesion)) {
            sesiones.remove(token);
            sesion = null;
        }
        if (sesion == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar");
        }
        sesion.ultimoUso = Instant.now();
        return sesion;
    }

    private boolean vencida(Sesion sesion) {
        return sesion.getUltimoUso().plus(Duration.ofMinutes(minutosInactividad)).isBefore(Instant.now());
    }

    private void verificarNoBloqueado(String clave) {
        Intentos intentos = intentosLogin.get(clave);
        if (intentos == null || intentos.bloqueadoHasta == null) {
            return;
        }
        if (intentos.bloqueadoHasta.isAfter(Instant.now())) {
            long minutos = Duration.between(Instant.now(), intentos.bloqueadoHasta).toMinutes() + 1;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Probá de nuevo en " + minutos + " minutos");
        }
        intentosLogin.remove(clave);
    }

    private void registrarFallo(String clave) {
        intentosLogin.compute(clave, (k, intentos) -> {
            if (intentos == null) {
                intentos = new Intentos();
            }
            intentos.fallidos++;
            if (intentos.fallidos >= MAX_INTENTOS_FALLIDOS) {
                intentos.bloqueadoHasta = Instant.now().plus(BLOQUEO_LOGIN);
            }
            return intentos;
        });
    }

    public Sesion requerirManicurista(String authorization) {
        Sesion sesion = requerirSesion(authorization);
        if (!ROL_MANICURISTA.equals(sesion.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta opción es solo para manicuristas");
        }
        return sesion;
    }

    public Sesion requerirAdmin(String authorization) {
        Sesion sesion = requerirSesion(authorization);
        if (!ROL_ADMIN.equals(sesion.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta opción es solo para el administrador");
        }
        return sesion;
    }

    // Manicuristas y admin (los clientes quedan afuera)
    public Sesion requerirPersonal(String authorization) {
        Sesion sesion = requerirSesion(authorization);
        if (ROL_CLIENTE.equals(sesion.getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés permiso para esta opción");
        }
        return sesion;
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
