package com.franelas.cotton.perfil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HU 1.1.2 - Crear perfil: reglas de autorización por rol en el controlador.
 * El servicio se reemplaza por un Mock (Mockito) para aislar la unidad bajo prueba.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU 1.1.2 - Crear perfil (PerfilController con Mockito)")
class PerfilControllerTest {

    @Mock
    private PerfilService perfilService;

    private PerfilController controller;

    @BeforeEach
    void setUp() {
        controller = new PerfilController(perfilService);
    }

    private Perfil perfil(String rol) {
        return new Perfil(0, "Nuevo", "nuevo@correo.com", "clave", "Dir", "0414", rol);
    }

    // ------------------------------------------------------------------ camino feliz

    @Test
    @DisplayName("Un ADMIN puede crear un perfil con rol ADMIN; responde 200 sin exponer la clave")
    void registrar_comoAdmin_conservaRolYOcultaClave() {
        // Arrange
        Perfil nuevo = perfil("ADMIN");
        Perfil creado = new Perfil(5, "Nuevo", "nuevo@correo.com", "clave", "Dir", "0414", "ADMIN");
        when(perfilService.registrarPerfil(any())).thenReturn(null);
        when(perfilService.buscarPorCorreo("nuevo@correo.com")).thenReturn(creado);

        // Act
        ResponseEntity<?> resp = controller.registrarPerfil(nuevo, "admin");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Perfil body = (Perfil) resp.getBody();
        assertNotNull(body);
        assertNull(body.getClave(), "La clave no debe devolverse al cliente");

        ArgumentCaptor<Perfil> captor = ArgumentCaptor.forClass(Perfil.class);
        verify(perfilService).registrarPerfil(captor.capture());
        assertEquals("ADMIN", captor.getValue().getRol());
    }

    @Test
    @DisplayName("ADMIN puede consultar todos los perfiles")
    void consultarTodos_comoAdmin_200() {
        when(perfilService.obtenerTodosLosPerfiles()).thenReturn(List.of(perfil("CLIENTE")));

        ResponseEntity<?> resp = controller.consultarTodosLosPerfiles("ADMIN");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, ((List<?>) resp.getBody()).size());
    }

    @Test
    @DisplayName("Login correcto responde 200 y oculta la clave")
    void login_correcto_200() {
        LoginRequest req = new LoginRequest();
        req.setCorreo("a@b.com");
        req.setClave("123");
        when(perfilService.buscarPorCorreoYClave("a@b.com", "123")).thenReturn(perfil("CLIENTE"));

        ResponseEntity<?> resp = controller.login(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNull(((Perfil) resp.getBody()).getClave());
    }

    // ------------------------------------------------------------------ casos de falla

    @Test
    @DisplayName("Si quien registra NO es ADMIN, el rol se fuerza a CLIENTE (no puede auto-asignarse ADMIN)")
    void registrar_sinRolAdmin_fuerzaRolCliente() {
        Perfil nuevo = perfil("ADMIN");
        when(perfilService.registrarPerfil(any())).thenReturn(null);

        controller.registrarPerfil(nuevo, null);

        ArgumentCaptor<Perfil> captor = ArgumentCaptor.forClass(Perfil.class);
        verify(perfilService).registrarPerfil(captor.capture());
        assertEquals("CLIENTE", captor.getValue().getRol());
    }

    @Test
    @DisplayName("Si el servicio reporta un error (correo en uso), responde 400 con el mensaje")
    void registrar_errorDeValidacion_400() {
        when(perfilService.registrarPerfil(any())).thenReturn("Error: El correo x ya esta en uso.");

        ResponseEntity<?> resp = controller.registrarPerfil(perfil("CLIENTE"), "ADMIN");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Error: El correo x ya esta en uso.", resp.getBody());
        verify(perfilService, never()).buscarPorCorreo(any());
    }

    @Test
    @DisplayName("Un CLIENTE no puede listar todos los perfiles (403)")
    void consultarTodos_comoCliente_403() {
        ResponseEntity<?> resp = controller.consultarTodosLosPerfiles("CLIENTE");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verifyNoInteractions(perfilService);
    }

    @Test
    @DisplayName("Login con credenciales inválidas responde 401")
    void login_incorrecto_401() {
        LoginRequest req = new LoginRequest();
        req.setCorreo("a@b.com");
        req.setClave("mal");
        when(perfilService.buscarPorCorreoYClave("a@b.com", "mal")).thenReturn(null);

        ResponseEntity<?> resp = controller.login(req);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("Un CLIENTE no puede editar el perfil de otro usuario (403)")
    void editar_perfilAjeno_403() {
        Perfil original = new Perfil(2, "Ana", "ana@correo.com", "c", "d", "t", "CLIENTE");
        when(perfilService.buscarPorId(2)).thenReturn(original);

        ResponseEntity<?> resp = controller.editarPerfil(2, perfil("CLIENTE"), "CLIENTE", "otro@correo.com");

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        verify(perfilService, never()).actualizarPerfil(any(), any());
    }

    @Test
    @DisplayName("Un CLIENTE que edita su propio perfil no puede cambiar su rol")
    void editar_propioPerfil_noCambiaRol() {
        Perfil original = new Perfil(2, "Ana", "ana@correo.com", "c", "d", "t", "CLIENTE");
        Perfil editado = new Perfil(0, "Ana", "ana@correo.com", "c", "d", "t", "ADMIN");
        when(perfilService.buscarPorId(2)).thenReturn(original);
        when(perfilService.actualizarPerfil(any(), any())).thenReturn(null);

        ResponseEntity<?> resp = controller.editarPerfil(2, editado, "CLIENTE", "ANA@correo.com");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("CLIENTE", editado.getRol());
    }

    @Test
    @DisplayName("Mi perfil sin cabecera de correo responde 400")
    void miPerfil_sinCorreo_400() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.miPerfil(" ").getStatusCode());
    }

    @Test
    @DisplayName("Eliminar perfil inexistente responde 400")
    void eliminar_inexistente_400() {
        when(perfilService.buscarPorId(99)).thenReturn(null);

        assertEquals(HttpStatus.BAD_REQUEST, controller.eliminarPerfil(99, "ADMIN", null).getStatusCode());
    }
}
