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

    // Quién hace el pedido, leído del token JWT. Los controllers la piden como parámetro (ver SesionArgumentResolver)
    @Getter
    public static class Sesion {
        private final Long idUsuario;
        private final String rol;

        public Sesion(Long idUsuario, String rol) {
            this.idUsuario = idUsuario;
            this.rol = rol;
        }

        public static Sesion desde(Jwt jwt) {
            return new Sesion(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.CLAIM_ROL));
        }
    }

    // Intentos de login fallidos por email; al llegar al máximo el email queda bloqueado un rato
    private static class Intentos {
        private int fallidos;
        private Instant bloqueadoHasta;
    }

    private final Map<String, Intentos> intentosLogin = new ConcurrentHashMap<>();
    private final UsuariosRepository usuariosRepository;
    private final TiposUsuarioRepository tiposRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuariosService usuariosService;
    private final JwtService jwtService;
    private final TokensCerradosService tokensCerrados;

    public AuthService(UsuariosRepository usuariosRepository, TiposUsuarioRepository tiposRepository,
                       PasswordEncoder passwordEncoder, UsuariosService usuariosService, JwtService jwtService,
                       TokensCerradosService tokensCerrados) {
        this.tokensCerrados = tokensCerrados;
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

    // /auth/logout es una ruta pública (Spring Security no autentica el pedido), así que el token se lee acá
    public void logout(String authorization) {
        try {
            Jwt jwt = jwtService.leer(token(authorization));
            tokensCerrados.cerrar(jwt.getId(), jwt.getExpiresAt());
        } catch (JwtException e) {
            // Token inválido o vencido: no hay nada que cerrar
        }
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

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
