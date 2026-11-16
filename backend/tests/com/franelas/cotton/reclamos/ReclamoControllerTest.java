package com.franelas.cotton.reclamos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HU 1.1.5 - Crear reclamo: reglas de permisos del controlador (servicio simulado con Mockito).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU 1.1.5 - Crear reclamo (ReclamoController con Mockito)")
class ReclamoControllerTest {

    @Mock
    private ReclamoService reclamoService;

    private ReclamoController controller;

    @BeforeEach
    void setUp() {
        controller = new ReclamoController(reclamoService);
    }

    // ------------------------------------------------------------------ camino feliz

    @Test
    @DisplayName("El reclamo se asocia al usuario autenticado (cabecera X-User-Email)")
    void crear_asignaUsuarioDesdeCabecera() {
        Reclamo nuevo = new Reclamo();
        nuevo.setUsuario("suplantado@correo.com");
        when(reclamoService.crearReclamo(any())).thenReturn(true);

        ResponseEntity<Boolean> resp = controller.crear(nuevo, "  ana@correo.com ");

        assertEquals(Boolean.TRUE, resp.getBody());
        assertEquals("ana@correo.com", nuevo.getUsuario());
    }

    @Test
    @DisplayName("ADMIN ve todos los reclamos")
    void listar_comoAdmin_todos() {
        when(reclamoService.obtenerTodos()).thenReturn(List.of(new Reclamo(), new Reclamo()));

        ResponseEntity<List<Reclamo>> resp = controller.listar("admin", "ana@correo.com");

        assertEquals(2, resp.getBody().size());
        verify(reclamoService, never()).obtenerPorUsuario(any());
    }

    @Test
    @DisplayName("Un CLIENTE ve solo sus reclamos")
    void listar_comoCliente_soloLosSuyos() {
        when(reclamoService.obtenerPorUsuario("ana@correo.com")).thenReturn(List.of(new Reclamo()));

        ResponseEntity<List<Reclamo>> resp = controller.listar("CLIENTE", "ana@correo.com");

        assertEquals(1, resp.getBody().size());
        verify(reclamoService, never()).obtenerTodos();
    }

    @Test
    @DisplayName("ADMIN puede actualizar el estado (limpia comillas del cuerpo)")
    void actualizarEstado_comoAdmin_ok() {
        when(reclamoService.actualizarEstado(1L, "CERRADO")).thenReturn(true);

        ResponseEntity<Boolean> resp = controller.actualizarEstado(1L, "\"CERRADO\"", "ADMIN");

        assertEquals(Boolean.TRUE, resp.getBody());
    }

    // ------------------------------------------------------------------ casos de falla

    @Test
    @DisplayName("Un CLIENTE no puede cambiar el estado de un reclamo")
    void actualizarEstado_comoCliente_false() {
        ResponseEntity<Boolean> resp = controller.actualizarEstado(1L, "CERRADO", "CLIENTE");

        assertEquals(Boolean.FALSE, resp.getBody());
        verifyNoInteractions(reclamoService);
    }

    @Test
    @DisplayName("Sin rol no se puede eliminar un reclamo")
    void eliminar_sinRol_false() {
        ResponseEntity<Boolean> resp = controller.eliminar(1L, null);

        assertEquals(Boolean.FALSE, resp.getBody());
        verifyNoInteractions(reclamoService);
    }

    @Test
    @DisplayName("Un reclamo nulo se rechaza sin llamar al servicio")
    void crear_nulo_false() {
        assertEquals(Boolean.FALSE, controller.crear(null, "ana@correo.com").getBody());
        verifyNoInteractions(reclamoService);
    }

    @Test
    @DisplayName("Si el servicio rechaza el reclamo (descripción corta), responde false")
    void crear_invalido_false() {
        when(reclamoService.crearReclamo(any())).thenReturn(false);

        assertEquals(Boolean.FALSE, controller.crear(new Reclamo(), "ana@correo.com").getBody());
    }
}
