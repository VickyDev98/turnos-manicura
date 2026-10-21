package com.victoria.turnos_manicura.service;

import com.victoria.turnos_manicura.exception.ValidacionException;
import com.victoria.turnos_manicura.model.ServiciosModel;
import com.victoria.turnos_manicura.repository.ServiciosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiciosServiceTest {

    @Mock ServiciosRepository repository;
    @InjectMocks ServiciosService service;

    @BeforeEach
    void preparar() {
        when(repository.save(any(ServiciosModel.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void guardar_servicioValido_quedaActivoPorDefectoYSinEspacios() {
        ServiciosModel guardado = service.guardar(servicio("  Spa de manos  ", LocalTime.of(0, 40), null));

        assertEquals("Spa de manos", guardado.getNombre());
        assertTrue(guardado.getActivo());
    }

    @Test
    void guardar_respetaElActivoQueVieneEnElPedido() {
        assertFalse(service.guardar(servicio("Retiro", LocalTime.of(0, 20), false)).getActivo());
    }

    @Test
    void guardar_nombreRepetido_lanzaValidacion() {
        when(repository.existsByNombreIgnoreCase("Spa de manos")).thenReturn(true);

        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(servicio("Spa de manos", LocalTime.of(0, 40), true)));

        assertEquals("nombre", e.getCampo());
        verify(repository, never()).save(any());
    }

    @Test
    void guardar_duracionMenorACincoMinutos_lanzaValidacion() {
        ValidacionException e = assertThrows(ValidacionException.class,
                () -> service.guardar(servicio("Rapido", LocalTime.of(0, 4), true)));

        assertEquals("duracion", e.getCampo());
    }

    @Test
    void guardar_duracionMayorAOchoHoras_lanzaValidacion() {
        assertThrows(ValidacionException.class,
                () -> service.guardar(servicio("Eterno", LocalTime.of(8, 1), true)));
    }

    @Test
    void guardar_duracionEnLosLimites_esValida() {
        assertDoesNotThrow(() -> service.guardar(servicio("Minimo", LocalTime.of(0, 5), true)));
        assertDoesNotThrow(() -> service.guardar(servicio("Maximo", LocalTime.of(8, 0), true)));
    }

    @Test
    void actualizar_servicioInexistente_lanzaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.actualizar(1L, servicio("Nuevo", LocalTime.of(1, 0), true)));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void actualizar_nombreDeOtroServicio_lanzaValidacion() {
        ServiciosModel actual = servicio("Actual", LocalTime.of(1, 0), true);
        actual.setId(1L);
        ServiciosModel otro = servicio("Nuevo", LocalTime.of(1, 0), true);
        otro.setId(2L);
        when(repository.findById(1L)).thenReturn(Optional.of(actual));
        when(repository.findByNombreIgnoreCase("Nuevo")).thenReturn(Optional.of(otro));

        assertThrows(ValidacionException.class,
                () -> service.actualizar(1L, servicio("Nuevo", LocalTime.of(1, 0), true)));
    }

    @Test
    void actualizar_conSuPropioNombre_actualizaLosDatos() {
        ServiciosModel actual = servicio("Actual", LocalTime.of(1, 0), true);
        actual.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(actual));
        when(repository.findByNombreIgnoreCase("Actual")).thenReturn(Optional.of(actual));

        ServiciosModel datos = servicio("Actual", LocalTime.of(2, 0), false);
        datos.setPrecio(9000);

        ServiciosModel editado = service.actualizar(1L, datos);

        assertEquals(LocalTime.of(2, 0), editado.getDuracion());
        assertEquals(9000, editado.getPrecio());
        assertFalse(editado.getActivo());
    }

    private ServiciosModel servicio(String nombre, LocalTime duracion, Boolean activo) {
        ServiciosModel s = new ServiciosModel();
        s.setNombre(nombre);
        s.setDuracion(duracion);
        s.setPrecio(5000);
        s.setActivo(activo);
        return s;
    }
}
