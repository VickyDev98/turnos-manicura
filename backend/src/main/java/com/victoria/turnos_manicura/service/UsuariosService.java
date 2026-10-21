package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.dto.UsuarioUpdate;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service

public class UsuariosService {
    private final UsuariosRepository repository;
    private final TiposUsuarioRepository tiposRepository;
    private final PasswordEncoder passwordEncoder;
    private final TurnosRepository turnosRepository;

    public UsuariosService(UsuariosRepository repository, TiposUsuarioRepository tiposRepository,
                           PasswordEncoder passwordEncoder, TurnosRepository turnosRepository) {
        this.turnosRepository = turnosRepository;
        this.repository = repository;
        this.tiposRepository = tiposRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuariosModel> listar() {
        return repository.findAll();
    }

    // El admin ve todo. El resto ve los nombres de todos, pero los datos de contacto solo de las
    // manicuristas y los propios (se devuelven copias: no se tocan las entidades guardadas)
    public List<UsuariosModel> listarPara(AuthService.Sesion sesion) {
        List<UsuariosModel> usuarios = repository.findAll();
        if (AuthService.ROL_ADMIN.equals(sesion.getRol())) {
            return usuarios;
        }

        Long idManicurista = tiposRepository.findByDescripcionIgnoreCase(AuthService.ROL_MANICURISTA)
                .map(t -> t.getId()).orElse(null);

        return usuarios.stream().map(u -> {
            UsuariosModel copia = new UsuariosModel();
            copia.setId(u.getId());
            copia.setTipo(u.getTipo());
            copia.setNombre(u.getNombre());
            copia.setApellido(u.getApellido());
            copia.setActivo(u.isActivo());
            boolean visible = u.getId().equals(sesion.getIdUsuario())
                    || (idManicurista != null && u.getTipo() == idManicurista.intValue());
            if (visible) {
                copia.setNumeroContacto(u.getNumeroContacto());
                copia.setEmail(u.getEmail());
                copia.setDireccion(u.getDireccion());
            }
            return copia;
        }).collect(java.util.stream.Collectors.toList());
    }

    public UsuariosModel guardar(UsuariosModel usuario) {
        usuario.setNombre(usuario.getNombre().trim());
        usuario.setApellido(usuario.getApellido().trim());
        boolean tieneDireccion = usuario.getDireccion() != null && !usuario.getDireccion().isBlank();
        usuario.setDireccion(tieneDireccion ? usuario.getDireccion().trim() : null);
        boolean tieneEmail = usuario.getEmail() != null && !usuario.getEmail().isBlank();
        usuario.setEmail(tieneEmail ? usuario.getEmail().trim().toLowerCase() : null);

        if (!tiposRepository.existsById((long) usuario.getTipo())) {
            throw new ValidacionException("tipo", "El tipo de usuario no existe");
        }
        if (!tieneEmail) {
            throw new ValidacionException("email", "El email es obligatorio para poder iniciar sesión");
        }
        if (usuario.getPassword() == null || usuario.getPassword().length() < 6) {
            throw new ValidacionException("password", "La contraseña tiene que tener al menos 6 caracteres");
        }
        if (usuario.getNumeroContacto() != 0 && usuario.getNumeroContacto() < 1000000) {
            throw new ValidacionException("numeroContacto", "El teléfono tiene que tener entre 7 y 10 dígitos");
        }
        if (tieneEmail && repository.existsByEmailIgnoreCase(usuario.getEmail())) {
            throw new ValidacionException("email", "Ya hay un usuario registrado con ese email");
        }

        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setActivo(true);

        return repository.save(usuario);
    }

    // Edición por el admin (el tipo no se cambia). La contraseña solo se cambia si se manda una nueva
    public UsuariosModel actualizar(Long id, UsuarioUpdate datos) {
        UsuariosModel usuario = buscar(id);

        String email = datos.getEmail().trim().toLowerCase();
        if (repository.findByEmailIgnoreCase(email).filter(otro -> !otro.getId().equals(id)).isPresent()) {
            throw new ValidacionException("email", "Ya hay un usuario registrado con ese email");
        }
        if (datos.getNumeroContacto() != 0 && datos.getNumeroContacto() < 1000000) {
            throw new ValidacionException("numeroContacto", "El teléfono tiene que tener entre 7 y 10 dígitos");
        }
        boolean cambiaPassword = datos.getPassword() != null && !datos.getPassword().isEmpty();
        if (cambiaPassword && datos.getPassword().length() < 6) {
            throw new ValidacionException("password", "La contraseña tiene que tener al menos 6 caracteres");
        }

        usuario.setNombre(datos.getNombre().trim());
        usuario.setApellido(datos.getApellido().trim());
        usuario.setNumeroContacto(datos.getNumeroContacto());
        usuario.setEmail(email);
        boolean tieneDireccion = datos.getDireccion() != null && !datos.getDireccion().isBlank();
        usuario.setDireccion(tieneDireccion ? datos.getDireccion().trim() : null);
        if (cambiaPassword) {
            usuario.setPassword(passwordEncoder.encode(datos.getPassword()));
        }
        return repository.save(usuario);
    }

    // Baja o reactivación. No se puede dar de baja a quien tiene turnos por delante (hay que cancelarlos o reprogramarlos)
    public UsuariosModel cambiarActivo(Long id, boolean activo, Long idAdmin) {
        UsuariosModel usuario = buscar(id);

        if (!activo) {
            if (usuario.getId().equals(idAdmin)) {
                throw new ValidacionException("activo", "No podés darte de baja a vos mismo");
            }
            LocalDateTime ahora = LocalDateTime.now();
            List<TurnosModel> pendientes = turnosRepository
                    .findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(id.intValue(), ahora);
            pendientes.addAll(turnosRepository
                    .findByIdClienteAndActivoTrueAndDiaHorarioGreaterThanEqual(id.intValue(), ahora));
            if (!pendientes.isEmpty()) {
                throw new ValidacionException("activo", usuario.getNombre() + " tiene " + pendientes.size()
                        + (pendientes.size() == 1 ? " turno pendiente" : " turnos pendientes")
                        + ": cancelalos o reprogramalos antes de dar de baja");
            }
        }

        usuario.setActivo(activo);
        return repository.save(usuario);
    }

    private UsuariosModel buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));
    }
}
