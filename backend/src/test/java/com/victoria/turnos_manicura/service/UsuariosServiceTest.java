package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.UsuarioUpdate;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.model.TurnosModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.TurnosRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuariosServiceTest {

    @Mock UsuariosRepository repository;
    @Mock TiposUsuarioRepository tiposRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TurnosRepository turnosRepository;
    @InjectMocks UsuariosService service;

    @BeforeEach
    void preparar() {
        when(tiposRepository.existsById(1L)).thenReturn(true);
        when(passwordEncoder.encode(any())).thenAnswer(i -> "hash(" + i.getArgument(0) + ")");
        when(repository.save(any(UsuariosModel.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- guardar ----------

    @Test
    void guardar_usuarioValido_normalizaDatosEncriptaYLoDejaActivo() {
        UsuariosModel guardado = service.guardar(nuevo("  Maria ", " Perez ", "  MARIA@Mail.com ", "123456"));

        assertEquals("Maria", guardado.getNombre());
        assertEquals("Perez", guardado.getApellido());
        assertEquals("maria@mail.com", guardado.getEmail());
        assertEquals("hash(123456)", guardado.getPassword());
        assertTrue(guardado.isActivo());
    }

    @Test
    void guardar_direccionEnBlancoQuedaNull() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setDireccion("   ");

        assertNull(service.guardar(usuario).getDireccion());
    }

    @Test
    void guardar_tipoInexistente_lanzaValidacion() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setTipo(99);

        ValidacionException e = assertThrows(ValidacionException.class, () -> service.guardar(usuario));

        assertEquals("tipo", e.getCampo());
    }

    @Test
    void guardar_sinEmail_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(nuevo("Ana", "Gomez", "  ", "123456")));

        assertEquals("email", e.getCampo());
    }

    @Test
    void guardar_passwordCorta_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(nuevo("Ana", "Gomez", "ana@mail.com", "12345")));

        assertEquals("password", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void guardar_telefonoDeMenosDeDiezDigitos_lanzaValidacion() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setNumeroContacto("12345");

        ValidacionException e = assertThrows(ValidacionException.class, () -> service.guardar(usuario));

        assertEquals("numeroContacto", e.getCampo());
    }

    @Test
    void guardar_telefonoConLetrasOGuiones_lanzaValidacion() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setNumeroContacto("11-3456-7890");

        ValidacionException e = assertThrows(ValidacionException.class, () -> service.guardar(usuario));

        assertEquals("numeroContacto", e.getCampo());
    }

    @Test
    void guardar_celularConCodigoDePaisEsValido() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setNumeroContacto("5491134567890");

        assertDoesNotThrow(() -> service.guardar(usuario));
        assertEquals("5491134567890", usuario.getNumeroContacto());
    }

    @Test
    void guardar_telefonoMayorAUnIntEsValido() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setNumeroContacto("3515551234");

        assertDoesNotThrow(() -> service.guardar(usuario));
    }

    @Test
    void guardar_sinTelefonoQuedaNull() {
        UsuariosModel usuario = nuevo("Ana", "Gomez", "ana@mail.com", "123456");
        usuario.setNumeroContacto("  ");

        assertDoesNotThrow(() -> service.guardar(usuario));
        assertNull(usuario.getNumeroContacto());
    }

    @Test
    void guardar_emailRepetido_lanzaValidacion() {
        when(repository.existsByEmailIgnoreCase("ana@mail.com")).thenReturn(true);

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(nuevo("Ana", "Gomez", "ana@mail.com", "123456")));

        assertEquals("email", e.getCampo());
    }

    // ---------- actualizar ----------

    @Test
    void actualizar_sinPassword_noCambiaLaContrasenaGuardada() {
        UsuariosModel existente = existente(5L, "ana@mail.com");
        existente.setPassword("hashViejo");
        when(repository.findById(5L)).thenReturn(Optional.of(existente));

        UsuariosModel editado = service.actualizar(5L, datos("Ana Maria", "ana@mail.com", ""));

        assertEquals("Ana Maria", editado.getNombre());
        assertEquals("hashViejo", editado.getPassword());
    }

    @Test
    void actualizar_conPasswordNueva_laEncripta() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, "ana@mail.com")));

        UsuariosModel editado = service.actualizar(5L, datos("Ana", "ana@mail.com", "nueva123"));

        assertEquals("hash(nueva123)", editado.getPassword());
    }

    @Test
    void actualizar_passwordNuevaCorta_lanzaValidacion() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, "ana@mail.com")));

        assertThrows(ValidacionException.class, () -> service.actualizar(5L, datos("Ana", "ana@mail.com", "123")));
    }

    @Test
    void actualizar_emailDeOtroUsuario_lanzaValidacion() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, "ana@mail.com")));
        when(repository.findByEmailIgnoreCase("otro@mail.com")).thenReturn(Optional.of(existente(6L, "otro@mail.com")));

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.actualizar(5L, datos("Ana", "otro@mail.com", "")));

        assertEquals("email", e.getCampo());
    }

    @Test
    void actualizar_conSuPropioEmail_esValido() {
        UsuariosModel ana = existente(5L, "ana@mail.com");
        when(repository.findById(5L)).thenReturn(Optional.of(ana));
        when(repository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(ana));

        assertDoesNotThrow(() -> service.actualizar(5L, datos("Ana", "ana@mail.com", "")));
    }

    // ---------- cambiarActivo ----------

    @Test
    void cambiarActivo_darDeBajaSinTurnosPendientes_loDesactiva() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, "ana@mail.com")));
        when(turnosRepository.findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(anyInt(), any()))
                .thenReturn(new ArrayList<>());
        when(turnosRepository.findByIdClienteAndActivoTrueAndDiaHorarioGreaterThanEqual(anyInt(), any()))
                .thenReturn(new ArrayList<>());

        assertFalse(service.cambiarActivo(5L, false, 1L).isActivo());
    }

    @Test
    void cambiarActivo_conTurnosPendientes_lanzaValidacion() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, "ana@mail.com")));
        when(turnosRepository.findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(anyInt(), any()))
                .thenReturn(new ArrayList<>(List.of(new TurnosModel())));
        when(turnosRepository.findByIdClienteAndActivoTrueAndDiaHorarioGreaterThanEqual(anyInt(), any()))
                .thenReturn(new ArrayList<>());

        ValidacionException e = assertThrows(ValidacionException.class, () -> service.cambiarActivo(5L, false, 1L));

        assertEquals("activo", e.getCampo());
        assertTrue(e.getMessage().contains("1 turno pendiente"));
    }

    @Test
    void cambiarActivo_elAdminNoPuedeDarseDeBajaASiMismo() {
        when(repository.findById(1L)).thenReturn(Optional.of(existente(1L, "admin@mail.com")));

        assertThrows(ValidacionException.class, () -> service.cambiarActivo(1L, false, 1L));
    }

    @Test
    void cambiarActivo_reactivarNoRevisaTurnos() {
        UsuariosModel baja = existente(5L, "ana@mail.com");
        baja.setActivo(false);
        when(repository.findById(5L)).thenReturn(Optional.of(baja));

        assertTrue(service.cambiarActivo(5L, true, 1L).isActivo());
        verify(turnosRepository, never()).findByIdManicuristaAndActivoTrueAndDiaHorarioGreaterThanEqual(anyInt(), any());
    }

    // ---------- listarPara ----------

    @Test
    void listarPara_clienteVeContactoSoloDeManicuristasYPropio() {
        TiposUsuarioModel manicurista = new TiposUsuarioModel();
        manicurista.setId(2L);
        manicurista.setDescripcion("MANICURISTA");
        when(tiposRepository.findByDescripcionIgnoreCase("MANICURISTA")).thenReturn(Optional.of(manicurista));

        UsuariosModel ana = conTipo(existente(1L, "ana@mail.com"), 2);
        UsuariosModel yo = conTipo(existente(6L, "maria@mail.com"), 1);
        UsuariosModel otroCliente = conTipo(existente(7L, "laura@mail.com"), 1);
        when(repository.findAll()).thenReturn(List.of(ana, yo, otroCliente));

        List<UsuariosModel> vistos = service.listarPara(new AuthService.Sesion(6L, AuthService.ROL_CLIENTE));

        assertEquals("ana@mail.com", vistos.get(0).getEmail());
        assertEquals("maria@mail.com", vistos.get(1).getEmail());
        assertNull(vistos.get(2).getEmail());
        // Se devuelven copias: la entidad original no se toca
        assertEquals("laura@mail.com", otroCliente.getEmail());
    }

    @Test
    void listarPara_manicuristaVeContactoDeSusClientesPeroNoDeLosDeOtras() {
        TiposUsuarioModel manicurista = new TiposUsuarioModel();
        manicurista.setId(2L);
        manicurista.setDescripcion("MANICURISTA");
        when(tiposRepository.findByDescripcionIgnoreCase("MANICURISTA")).thenReturn(Optional.of(manicurista));

        UsuariosModel yo = conTipo(existente(1L, "ana@mail.com"), 2);
        UsuariosModel miCliente = conTipo(existente(6L, "maria@mail.com"), 1);
        UsuariosModel clienteAjeno = conTipo(existente(7L, "laura@mail.com"), 1);
        miCliente.setNumeroContacto("1134567801");
        when(repository.findAll()).thenReturn(List.of(yo, miCliente, clienteAjeno));
        when(turnosRepository.findIdsClientesDeManicurista(1)).thenReturn(List.of(6));

        List<UsuariosModel> vistos = service.listarPara(new AuthService.Sesion(1L, AuthService.ROL_MANICURISTA));

        assertEquals("1134567801", vistos.get(1).getNumeroContacto());
        assertNull(vistos.get(2).getEmail());
        assertNull(vistos.get(2).getNumeroContacto());
    }

    @Test
    void listarPara_adminVeTodoSinCambios() {
        UsuariosModel ana = existente(1L, "ana@mail.com");
        when(repository.findAll()).thenReturn(List.of(ana));

        List<UsuariosModel> vistos = service.listarPara(new AuthService.Sesion(50L, AuthService.ROL_ADMIN));

        assertSame(ana, vistos.get(0));
    }

    // ---------- helpers ----------

    private UsuariosModel nuevo(String nombre, String apellido, String email, String password) {
        UsuariosModel u = new UsuariosModel();
        u.setTipo(1);
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email);
        u.setPassword(password);
        u.setNumeroContacto("1134567890");
        return u;
    }

    private UsuariosModel existente(Long id, String email) {
        UsuariosModel u = new UsuariosModel();
        u.setId(id);
        u.setTipo(1);
        u.setNombre("Nombre" + id);
        u.setApellido("Apellido");
        u.setEmail(email);
        u.setActivo(true);
        return u;
    }

    private UsuariosModel conTipo(UsuariosModel u, int tipo) {
        u.setTipo(tipo);
        return u;
    }

    private UsuarioUpdate datos(String nombre, String email, String password) {
        UsuarioUpdate d = new UsuarioUpdate();
        d.setNombre(nombre);
        d.setApellido("Gomez");
        d.setEmail(email);
        d.setPassword(password);
        d.setNumeroContacto("1134567890");
        return d;
    }
}
