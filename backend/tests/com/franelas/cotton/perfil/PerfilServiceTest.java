package com.franelas.cotton.perfil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.franelas.cotton.support.DatosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 1.1.2 - Crear perfil.
 * "Como Administrador, quiero registrar los datos de un nuevo usuario y asignarle un rol
 * específico para crear su perfil y habilitar su acceso al sistema."
 */
@DisplayName("HU 1.1.2 - Crear perfil (PerfilService)")
class PerfilServiceTest {

    private static final String ARCHIVO = "perfiles.json";
    private static final TypeReference<List<Perfil>> TIPO = new TypeReference<>() {};

    private PerfilService service;

    @BeforeEach
    void setUp() {
        DatosDePrueba.limpiar();
        DatosDePrueba.escribir(ARCHIVO, List.of(
                new Perfil(1, "Admin", "admin@cotton.com", "admin123", "Caracas", "0414-0000000", "ADMIN"),
                new Perfil(2, "Ana Cliente", "ana@correo.com", "clave1", "Valencia", "0412-1111111", "CLIENTE")
        ));
        service = new PerfilService();
    }

    @AfterEach
    void tearDown() {
        DatosDePrueba.limpiar();
    }

    /** Test Data Builder sencillo para crear perfiles válidos. */
    private Perfil perfilValido() {
        return new Perfil(0, "Luis Pérez", "luis@correo.com", "secreta", "Maracay", "0424-2222222", "CLIENTE");
    }

    // ------------------------------------------------------------------ camino feliz
    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Registra un perfil válido y lo agrega a la lista")
        void registrarPerfilValido_seGuardaEnJson() {
            // Arrange
            Perfil nuevo = perfilValido();

            // Act
            String error = service.registrarPerfil(nuevo);

            // Assert
            assertNull(error, "No debe haber mensaje de error");
            List<Perfil> perfiles = DatosDePrueba.leer(ARCHIVO, TIPO);
            assertEquals(3, perfiles.size());
            assertEquals("luis@correo.com", perfiles.get(2).getCorreo());
        }

        @Test
        @DisplayName("Asigna un ID único secuencial al nuevo perfil")
        void registrarPerfil_asignaIdSecuencial() {
            Perfil nuevo = perfilValido();

            service.registrarPerfil(nuevo);

            assertEquals(3, nuevo.getId());
        }

        @Test
        @DisplayName("Asigna rol CLIENTE por defecto si no se especifica")
        void registrarPerfilSinRol_asignaCliente() {
            Perfil nuevo = perfilValido();
            nuevo.setRol(null);

            assertNull(service.registrarPerfil(nuevo));
            assertEquals("CLIENTE", nuevo.getRol());
        }

        @Test
        @DisplayName("Normaliza el rol a mayúsculas (admin -> ADMIN)")
        void registrarPerfil_normalizaRol() {
            Perfil nuevo = perfilValido();
            nuevo.setRol("  admin ");

            assertNull(service.registrarPerfil(nuevo));
            assertEquals("ADMIN", nuevo.getRol());
        }

        @Test
        @DisplayName("Busca un perfil por correo sin distinguir mayúsculas")
        void buscarPorCorreo_ignoraMayusculas() {
            Perfil p = service.buscarPorCorreo("  ANA@Correo.com ");

            assertNotNull(p);
            assertEquals(2, p.getId());
        }

        @Test
        @DisplayName("Login con credenciales correctas devuelve el perfil")
        void buscarPorCorreoYClave_correctas_devuelvePerfil() {
            Perfil p = service.buscarPorCorreoYClave("admin@cotton.com", "admin123");

            assertNotNull(p);
            assertEquals("ADMIN", p.getRol());
        }

        @Test
        @DisplayName("Actualiza un perfil existente conservando la clave si viene vacía")
        void actualizarPerfil_conservaClaveSiVieneVacia() {
            Perfil original = service.buscarPorId(2);
            Perfil editado = new Perfil(2, "Ana María", "ana@correo.com", "", "Mérida", "0412-9999999", null);

            String error = service.actualizarPerfil(original, editado);

            assertNull(error);
            Perfil guardado = service.buscarPorId(2);
            assertEquals("Ana María", guardado.getNombre());
            assertEquals("clave1", guardado.getClave());
            assertEquals("CLIENTE", guardado.getRol());
        }

        @Test
        @DisplayName("Elimina un perfil existente")
        void eliminarPerfil_existente() {
            assertNull(service.eliminarPerfil(2));
            assertNull(service.buscarPorId(2));
        }
    }

    // ------------------------------------------------------------------ casos de falla
    @Nested
    @DisplayName("Casos de falla")
    class CasosDeFalla {

        @Test
        @DisplayName("Rechaza un correo ya registrado (\"ya está en uso\") y no crea el perfil")
        void registrarPerfil_correoDuplicado_retornaError() {
            Perfil nuevo = perfilValido();
            nuevo.setCorreo("ana@correo.com");

            String error = service.registrarPerfil(nuevo);

            assertNotNull(error);
            assertTrue(error.contains("ya esta en uso"));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("Detecta correo duplicado sin importar mayúsculas")
        void registrarPerfil_correoDuplicadoMayusculas_retornaError() {
            Perfil nuevo = perfilValido();
            nuevo.setCorreo("ANA@CORREO.COM");

            assertNotNull(service.registrarPerfil(nuevo));
        }

        @ParameterizedTest(name = "Nombre faltante: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Rechaza nombre faltante")
        void registrarPerfil_sinNombre_retornaError(String nombre) {
            Perfil nuevo = perfilValido();
            nuevo.setNombre(nombre);

            assertEquals("Error: Nombre obligatorio.", service.registrarPerfil(nuevo));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("Rechaza dirección faltante")
        void registrarPerfil_sinDireccion_retornaError() {
            Perfil nuevo = perfilValido();
            nuevo.setDireccion("");

            assertEquals("Error: Dirección obligatoria.", service.registrarPerfil(nuevo));
        }

        @Test
        @DisplayName("Rechaza teléfono faltante")
        void registrarPerfil_sinTelefono_retornaError() {
            Perfil nuevo = perfilValido();
            nuevo.setTelefono(null);

            assertEquals("Error: Teléfono obligatorio.", service.registrarPerfil(nuevo));
        }

        @Test
        @DisplayName("Rechaza clave faltante")
        void registrarPerfil_sinClave_retornaError() {
            Perfil nuevo = perfilValido();
            nuevo.setClave("  ");

            assertEquals("Error: Clave obligatoria.", service.registrarPerfil(nuevo));
        }

        @ParameterizedTest(name = "Correo inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"sinarroba.com", "a@b", "@correo.com", "luis@correo.", "luis @correo.com"})
        @DisplayName("Rechaza correos con formato inválido")
        void registrarPerfil_correoInvalido_retornaError(String correo) {
            Perfil nuevo = perfilValido();
            nuevo.setCorreo(correo);

            String error = service.registrarPerfil(nuevo);

            assertNotNull(error);
            assertTrue(error.contains("no tiene un formato valido"));
        }

        @ParameterizedTest(name = "Rol inválido: {0}")
        @ValueSource(strings = {"SUPERUSER", "VENDEDOR", "root"})
        @DisplayName("Rechaza roles distintos de ADMIN o CLIENTE")
        void registrarPerfil_rolInvalido_retornaError(String rol) {
            Perfil nuevo = perfilValido();
            nuevo.setRol(rol);

            assertEquals("Error: Rol inválido.", service.registrarPerfil(nuevo));
        }

        @Test
        @DisplayName("Rechaza un perfil nulo")
        void registrarPerfilNulo_retornaError() {
            assertEquals("Error: Perfil inválido.", service.registrarPerfil(null));
        }

        @Test
        @DisplayName("Login con clave incorrecta devuelve null")
        void buscarPorCorreoYClave_claveIncorrecta_null() {
            assertNull(service.buscarPorCorreoYClave("admin@cotton.com", "incorrecta"));
        }

        @Test
        @DisplayName("Login con datos nulos devuelve null")
        void buscarPorCorreoYClave_nulos_null() {
            assertNull(service.buscarPorCorreoYClave(null, "x"));
            assertNull(service.buscarPorCorreoYClave("admin@cotton.com", null));
        }

        @Test
        @DisplayName("No permite cambiar el correo a uno usado por otro perfil")
        void actualizarPerfil_correoDeOtro_retornaError() {
            Perfil original = service.buscarPorId(2);
            Perfil editado = new Perfil(2, "Ana", "admin@cotton.com", null, "Valencia", "0412", "CLIENTE");

            String error = service.actualizarPerfil(original, editado);

            assertNotNull(error);
            assertTrue(error.contains("ya esta en uso por otro perfil"));
        }

        @Test
        @DisplayName("No actualiza un perfil inexistente")
        void actualizarPerfil_inexistente_retornaError() {
            Perfil original = perfilValido();
            Perfil editado = perfilValido();
            editado.setId(99);

            assertEquals("Error: Perfil no encontrado para editar.", service.actualizarPerfil(original, editado));
        }

        @Test
        @DisplayName("No elimina un perfil inexistente")
        void eliminarPerfil_inexistente_retornaError() {
            assertEquals("Error: Perfil no encontrado para eliminar.", service.eliminarPerfil(99));
        }

        @Test
        @DisplayName("Devuelve lista vacía si perfiles.json está corrupto")
        void obtenerTodos_jsonCorrupto_listaVacia() {
            DatosDePrueba.escribirTexto(ARCHIVO, "not-json");

            assertTrue(service.obtenerTodosLosPerfiles().isEmpty());
        }
    }
}
