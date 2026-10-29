package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.dto.LoginRequest;
import com.victoria.turnos_manicura.dto.LoginResponse;
import com.victoria.turnos_manicura.dto.RegistroRequest;
import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.TiposUsuarioModel;
import com.victoria.turnos_manicura.model.UsuariosModel;
import com.victoria.turnos_manicura.repository.TiposUsuarioRepository;
import com.victoria.turnos_manicura.repository.UsuariosRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    // El token ya lo validó Spring Security antes de llegar al service (ver SesionVigenteValidatorTest y
    // JwtServiceTest): acá se prueba lo que el service lee del contexto y el logout

    @Test
    void requerirSesion_conUnUsuarioAutenticado_devuelveLaSesion() {
        autenticarComo(1L, "MANICURISTA");

        AuthService.Sesion sesion = service.requerirSesion();

        assertEquals(1L, sesion.getIdUsuario());
        assertEquals("MANICURISTA", sesion.getRol());
    }

    @Test
    void requerirSesion_sinAutenticacion_lanzaUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, () -> service.requerirSesion()).getStatusCode());
    }

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

    // ---------- roles ----------

    @Test
    void requerirManicurista_conUnaManicurista_pasa() {
        autenticarComo(1L, "MANICURISTA");

        assertEquals(1L, service.requerirManicurista().getIdUsuario());
    }

    @Test
    void requerirAdmin_conUnaManicurista_lanzaForbidden() {
        autenticarComo(1L, "MANICURISTA");

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.requerirAdmin()).getStatusCode());
    }

    @Test
    void requerirPersonal_dejaAfueraALosClientes() {
        autenticarComo(1L, "CLIENTE");

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.requerirPersonal()).getStatusCode());
    }

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

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // Deja en el contexto de Spring al usuario que, en un pedido real, habría autenticado el filtro de seguridad
    private void autenticarComo(Long idUsuario, String rol) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(String.valueOf(idUsuario)).claim(JwtService.CLAIM_ROL, rol).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

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
