package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UsuariosRepository usuariosRepository;
    @Mock TiposUsuarioRepository tiposRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UsuariosService usuariosService;
    @Mock TokensCerradosService tokensCerrados;
    // JwtService real (no simulado): así se prueba que el token emitido de verdad se puede leer
    private static final String CLAVE = "clave-de-prueba-solo-para-tests-0123456789";
    @Spy JwtService jwtService = new JwtService(CLAVE, 480);
    @InjectMocks AuthService service;

    private UsuariosModel ana;

    @BeforeEach
    void preparar() {
        ana = new UsuariosModel();
        ana.setId(1L);
        ana.setTipo(2);
        ana.setNombre("Ana");
        ana.setApellido("Gomez");
        ana.setEmail("ana@mail.com");
        ana.setPassword("hash");
        ana.setActivo(true);

        when(usuariosRepository.findByEmailIgnoreCase("ana@mail.com")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(tiposRepository.findById(2L)).thenReturn(Optional.of(tipo(2L, "manicurista")));
    }

    // ---------- login ----------

    @Test
    void login_credencialesCorrectas_devuelveTokenYRolEnMayusculas() {
        LoginResponse respuesta = service.login(login("ana@mail.com", "123456"));

        assertNotNull(respuesta.getToken());
        assertEquals(1L, respuesta.getId());
        assertEquals("MANICURISTA", respuesta.getRol());
    }

    @Test
    void login_ignoraEspaciosAlrededorDelEmail() {
        assertDoesNotThrow(() -> service.login(login("  ana@mail.com ", "123456")));
    }

    @Test
    void login_passwordIncorrecta_lanzaUnauthorized() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.login(login("ana@mail.com", "incorrecta")));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void login_emailInexistente_lanzaUnauthorized() {
        when(usuariosRepository.findByEmailIgnoreCase("nadie@mail.com")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.login(login("nadie@mail.com", "123456")));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void login_usuarioDadoDeBaja_lanzaUnauthorized() {
        ana.setActivo(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.login(login("ana@mail.com", "123456")));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
        assertTrue(e.getReason().contains("baja"));
    }

    // ---------- sesiones ----------
    // El token ya lo validó Spring Security antes de llegar al service (ver SesionVigenteValidatorTest,
    // JwtServiceTest y SesionArgumentResolverTest): acá se prueba el logout

    @Test
    void logout_anotaElTokenComoCerrado() {
        String token = service.login(login("ana@mail.com", "123456")).getToken();
        Jwt jwt = jwtService.leer(token);

        service.logout("Bearer " + token);

        verify(tokensCerrados).cerrar(jwt.getId(), jwt.getExpiresAt());
    }

    @Test
    void logout_conTokenInventadoOSinHeader_noHaceNada() {
        service.logout("Bearer inventado");
        service.logout(null);

        verify(tokensCerrados, never()).cerrar(any(), any());
    }

    // Los permisos por rol (@PreAuthorize) se prueban con MockMvc en los tests de integración (ver RolesIntegrationTest)

    // ---------- registrar ----------

    @Test
    void registrar_comoAdmin_lanzaValidacionYNoGuarda() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.registrar(registro("ADMIN")));

        assertEquals("rol", e.getCampo());
        verify(usuariosService, never()).guardar(any());
    }

    @Test
    void registrar_comoCliente_guardaSinDireccionYDejaLaSesionIniciada() {
        when(tiposRepository.findByDescripcionIgnoreCase("CLIENTE")).thenReturn(Optional.of(tipo(1L, "CLIENTE")));
        RegistroRequest pedido = registro("cliente");
        pedido.setDireccion("Calle Falsa 123");

        // Tras guardar, el login busca a la persona recién creada
        when(usuariosRepository.findByEmailIgnoreCase("nueva@mail.com")).thenReturn(Optional.of(ana));

        LoginResponse respuesta = service.registrar(pedido);

        org.mockito.ArgumentCaptor<UsuariosModel> captor = org.mockito.ArgumentCaptor.forClass(UsuariosModel.class);
        verify(usuariosService).guardar(captor.capture());
        assertEquals(1, captor.getValue().getTipo());
        assertNull(captor.getValue().getDireccion());
        assertNotNull(respuesta.getToken());
    }

    @Test
    void registrar_comoManicurista_guardaSuDireccion() {
        when(tiposRepository.findByDescripcionIgnoreCase("MANICURISTA")).thenReturn(Optional.of(tipo(2L, "MANICURISTA")));
        when(usuariosRepository.findByEmailIgnoreCase("nueva@mail.com")).thenReturn(Optional.of(ana));
        RegistroRequest pedido = registro("MANICURISTA");
        pedido.setDireccion("Calle Falsa 123");

        service.registrar(pedido);

        org.mockito.ArgumentCaptor<UsuariosModel> captor = org.mockito.ArgumentCaptor.forClass(UsuariosModel.class);
        verify(usuariosService).guardar(captor.capture());
        assertEquals("Calle Falsa 123", captor.getValue().getDireccion());
    }

    // ---------- bloqueo de login y vencimiento de sesión ----------

    @Test
    void login_cincoFallos_bloqueaElEmailAunqueLaClaveSeaCorrecta() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ResponseStatusException.class, () -> service.login(login("ana@mail.com", "mala")));
        }

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.login(login("ana@mail.com", "123456")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
    }

    @Test
    void login_exitosoReiniciaElContadorDeFallos() {
        for (int i = 0; i < 4; i++) {
            assertThrows(ResponseStatusException.class, () -> service.login(login("ana@mail.com", "mala")));
        }
        service.login(login("ana@mail.com", "123456"));

        for (int i = 0; i < 4; i++) {
            assertThrows(ResponseStatusException.class, () -> service.login(login("ana@mail.com", "mala")));
        }

        assertDoesNotThrow(() -> service.login(login("ana@mail.com", "123456")));
    }

    // ---------- helpers ----------



    private LoginRequest login(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private RegistroRequest registro(String rol) {
        RegistroRequest r = new RegistroRequest();
        r.setRol(rol);
        r.setNombre("Nueva");
        r.setApellido("Usuaria");
        r.setEmail("nueva@mail.com");
        r.setPassword("123456");
        r.setNumeroContacto("1134567890");
        return r;
    }

    private TiposUsuarioModel tipo(Long id, String descripcion) {
        TiposUsuarioModel t = new TiposUsuarioModel();
        t.setId(id);
        t.setDescripcion(descripcion);
        return t;
    }
}
