package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public static final String ROL_MANICURISTA = "MANICURISTA";
    public static final String ROL_CLIENTE = "CLIENTE";
    public static final String ROL_ADMIN = "ADMIN";

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(15);

    // Quién hace el pedido, leído del token JWT (ver requerirSesion)
    @Getter
    public static class Sesion {
        private final Long idUsuario;
        private final String rol;

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

    // Tokens cerrados con logout (id del token -> cuándo vence). Un JWT no se puede "borrar" porque el
    // servidor no lo guarda, así que se anota acá hasta que venza solo. Se pierde al reiniciar el backend
    private final Map<String, Instant> tokensCerrados = new ConcurrentHashMap<>();
    private final Map<String, Intentos> intentosLogin = new ConcurrentHashMap<>();
    private final UsuariosRepository usuariosRepository;
    private final TiposUsuarioRepository tiposRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuariosService usuariosService;
    private final JwtService jwtService;

    public AuthService(UsuariosRepository usuariosRepository, TiposUsuarioRepository tiposRepository,
                       PasswordEncoder passwordEncoder, UsuariosService usuariosService, JwtService jwtService) {
        this.jwtService = jwtService;
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

        String token = jwtService.emitir(usuario.getId(), rol);

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

    public void logout(String authorization) {
        tokensCerrados.values().removeIf(vence -> vence.isBefore(Instant.now()));
        try {
            Jwt jwt = jwtService.leer(token(authorization));
            tokensCerrados.put(jwt.getId(), jwt.getExpiresAt());
        } catch (JwtException e) {
            // Token inválido o vencido: no hay nada que cerrar
        }
    }

    // Recibe el header "Authorization: Bearer <token>". Un usuario dado de baja pierde el acceso
    // al instante, aunque su token siga vigente, porque se consulta si sigue activo en cada pedido
    public Sesion requerirSesion(String authorization) {
        Jwt jwt;
        try {
            jwt = jwtService.leer(token(authorization));
        } catch (JwtException e) {
            throw sinSesion();
        }
        Long idUsuario = Long.valueOf(jwt.getSubject());
        boolean activo = usuariosRepository.findById(idUsuario).map(UsuariosModel::isActivo).orElse(false);
        if (tokensCerrados.containsKey(jwt.getId()) || !activo) {
            throw sinSesion();
        }
        return new Sesion(idUsuario, jwt.getClaimAsString(JwtService.CLAIM_ROL));
    }

    private ResponseStatusException sinSesion() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar");
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
