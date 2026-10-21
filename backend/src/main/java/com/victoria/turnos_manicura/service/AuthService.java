package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public static final String ROL_MANICURISTA = "MANICURISTA";
    public static final String ROL_CLIENTE = "CLIENTE";
    public static final String ROL_ADMIN = "ADMIN";

    // Sesión en memoria: se pierde al reiniciar el backend y hay que volver a iniciar sesión
    @Getter
    @AllArgsConstructor
    public static class Sesion {
        private final Long idUsuario;
        private final String rol;
    }

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();
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
        UsuariosModel usuario = usuariosRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .filter(u -> u.getPassword() != null && passwordEncoder.matches(request.getPassword(), u.getPassword()))
                .map(u -> {
                    if (!u.isActivo()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tu cuenta está dada de baja");
                    }
                    return u;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos"));

        String rol = tiposRepository.findById((long) usuario.getTipo())
                .map(t -> t.getDescripcion().toUpperCase())
                .orElse("");

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
        Sesion sesion = sesiones.get(token(authorization));
        if (sesion == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Iniciá sesión para continuar");
        }
        return sesion;
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
