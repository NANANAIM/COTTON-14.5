package com.franelas.cotton.reclamos;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 1.1.5 - Crear reclamo.
 * "Como Usuario (Registrado), quiero crear un reclamo desde el módulo de atención al cliente
 * para reportar inconvenientes con mis pedidos o productos y recibir respuesta del administrador."
 */
@DisplayName("HU 1.1.5 - Crear reclamo (ReclamoService)")
class ReclamoServiceTest {

    private static final String ARCHIVO = "reclamos.json";
    private static final TypeReference<List<Reclamo>> TIPO = new TypeReference<>() {};

    /** Descripción de exactamente 50 caracteres (valor límite del criterio de aceptación). */
    private static final String DESC_50 = "x".repeat(50);
    private static final String DESC_VALIDA =
            "La franela llegó con una costura rota en la manga izquierda y el color no coincide.";

    private ReclamoService service;

    @BeforeEach
    void setUp() {
        DatosDePrueba.limpiar();
        DatosDePrueba.escribir(ARCHIVO, new ArrayList<>(List.of(
                new Reclamo(1, "ana@correo.com", "Talla", DESC_VALIDA, "2025-10-01", "PENDIENTE"),
                new Reclamo(2, "luis@correo.com", "Color", DESC_VALIDA, "2025-10-02", "EN_PROCESO")
        )));
        service = new ReclamoService();
    }

    @AfterEach
    void tearDown() {
        DatosDePrueba.limpiar();
    }

    private Reclamo reclamo(String usuario, String titulo, String descripcion) {
        Reclamo r = new Reclamo();
        r.setUsuario(usuario);
        r.setTitulo(titulo);
        r.setDescripcion(descripcion);
        return r;
    }

    // ------------------------------------------------------------------ camino feliz
    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Crea un reclamo válido y lo guarda en el JSON")
        void crearReclamoValido_seGuarda() {
            // Arrange
            Reclamo nuevo = reclamo("ana@correo.com", "Pedido incompleto", DESC_VALIDA);

            // Act
            boolean ok = service.crearReclamo(nuevo);

            // Assert
            assertTrue(ok);
            List<Reclamo> guardados = DatosDePrueba.leer(ARCHIVO, TIPO);
            assertEquals(3, guardados.size());
            assertEquals("Pedido incompleto", guardados.get(2).getTitulo());
        }

        @Test
        @DisplayName("El estado inicial es siempre PENDIENTE, aunque se envíe otro")
        void crearReclamo_estadoInicialPendiente() {
            Reclamo nuevo = reclamo("ana@correo.com", "Título", DESC_VALIDA);
            nuevo.setEstado("CERRADO");

            service.crearReclamo(nuevo);

            assertEquals("PENDIENTE", nuevo.getEstado());
        }

        @Test
        @DisplayName("Registra la fecha de creación (hoy) si no se envía")
        void crearReclamo_registraFechaCreacion() {
            Reclamo nuevo = reclamo("ana@correo.com", "Título", DESC_VALIDA);

            service.crearReclamo(nuevo);

            assertEquals(LocalDate.now().toString(), nuevo.getFechaCreacion());
        }

        @Test
        @DisplayName("Asigna un ID secuencial")
        void crearReclamo_asignaIdSecuencial() {
            Reclamo nuevo = reclamo("ana@correo.com", "Título", DESC_VALIDA);

            service.crearReclamo(nuevo);

            assertEquals(3, nuevo.getId());
        }

        @Test
        @DisplayName("Acepta una descripción de exactamente 50 caracteres (valor límite)")
        void crearReclamo_descripcion50Caracteres_esValida() {
            assertTrue(service.crearReclamo(reclamo("ana@correo.com", "Título", DESC_50)));
        }

        @ParameterizedTest(name = "Estado: {0}")
        @ValueSource(strings = {"EN_PROCESO", "CERRADO", "cerrado", " en_proceso "})
        @DisplayName("El administrador puede cambiar el estado a En proceso o Cerrado")
        void actualizarEstado_valido(String estado) {
            assertTrue(service.actualizarEstado(1, estado));

            Reclamo r = DatosDePrueba.leer(ARCHIVO, TIPO).stream().filter(x -> x.getId() == 1).findFirst().orElseThrow();
            assertEquals(estado.trim().toUpperCase(), r.getEstado());
        }

        @Test
        @DisplayName("El administrador visualiza todos los reclamos (más recientes primero)")
        void obtenerTodos_ordenDescendente() {
            List<Reclamo> todos = service.obtenerTodos();

            assertEquals(2, todos.size());
            assertEquals(2, todos.get(0).getId());
            assertEquals(1, todos.get(1).getId());
        }

        @Test
        @DisplayName("El usuario solo ve sus propios reclamos")
        void obtenerPorUsuario_filtraPorCorreo() {
            List<Reclamo> deAna = service.obtenerPorUsuario("  ANA@correo.com ");

            assertEquals(1, deAna.size());
            assertEquals("ana@correo.com", deAna.get(0).getUsuario());
        }

        @Test
        @DisplayName("Elimina un reclamo existente")
        void eliminarReclamo_existente() {
            assertTrue(service.eliminarReclamo(1));
            assertEquals(1, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }
    }

    // ------------------------------------------------------------------ casos de falla
    @Nested
    @DisplayName("Casos de falla")
    class CasosDeFalla {

        @Test
        @DisplayName("Rechaza una descripción de 49 caracteres (mínimo 50)")
        void crearReclamo_descripcion49Caracteres_retornaFalse() {
            assertFalse(service.crearReclamo(reclamo("ana@correo.com", "Título", "x".repeat(49))));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("No cuenta los espacios al inicio/fin para el mínimo de 50 caracteres")
        void crearReclamo_descripcionConRellenoDeEspacios_retornaFalse() {
            String desc = "   " + "x".repeat(48) + "    ";

            assertFalse(service.crearReclamo(reclamo("ana@correo.com", "Título", desc)));
        }

        @ParameterizedTest(name = "Descripción: \"{0}\"")
        @NullAndEmptySource
        @DisplayName("La descripción es obligatoria")
        void crearReclamo_sinDescripcion_retornaFalse(String desc) {
            assertFalse(service.crearReclamo(reclamo("ana@correo.com", "Título", desc)));
        }

        @ParameterizedTest(name = "Título: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("El título es obligatorio")
        void crearReclamo_sinTitulo_retornaFalse(String titulo) {
            assertFalse(service.crearReclamo(reclamo("ana@correo.com", titulo, DESC_VALIDA)));
        }

        @ParameterizedTest(name = "Usuario: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("El usuario (registrado) es obligatorio")
        void crearReclamo_sinUsuario_retornaFalse(String usuario) {
            assertFalse(service.crearReclamo(reclamo(usuario, "Título", DESC_VALIDA)));
        }

        @Test
        @DisplayName("Rechaza un reclamo nulo")
        void crearReclamoNulo_retornaFalse() {
            assertFalse(service.crearReclamo(null));
        }

        @ParameterizedTest(name = "Estado inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"RESUELTO", "EN PROCESO", "abierto"})
        @DisplayName("Rechaza estados distintos de PENDIENTE, EN_PROCESO o CERRADO")
        void actualizarEstado_invalido_retornaFalse(String estado) {
            assertFalse(service.actualizarEstado(1, estado));
            assertEquals("PENDIENTE", DatosDePrueba.leer(ARCHIVO, TIPO).get(0).getEstado());
        }

        @Test
        @DisplayName("No actualiza el estado de un reclamo inexistente")
        void actualizarEstado_inexistente_retornaFalse() {
            assertFalse(service.actualizarEstado(999, "CERRADO"));
        }

        @Test
        @DisplayName("No elimina un reclamo inexistente")
        void eliminarReclamo_inexistente_retornaFalse() {
            assertFalse(service.eliminarReclamo(999));
        }

        @Test
        @DisplayName("Consultar por usuario vacío devuelve lista vacía")
        void obtenerPorUsuario_vacio_listaVacia() {
            assertTrue(service.obtenerPorUsuario(" ").isEmpty());
            assertTrue(service.obtenerPorUsuario(null).isEmpty());
        }

        @Test
        @DisplayName("La lista devuelta es de solo lectura")
        void obtenerTodos_listaInmodificable() {
            List<Reclamo> todos = service.obtenerTodos();

            assertThrows(UnsupportedOperationException.class, () -> todos.add(new Reclamo()));
        }

        @Test
        @DisplayName("El servicio no arranca si reclamos.json no existe")
        void constructor_sinArchivo_lanzaExcepcion() {
            DatosDePrueba.limpiar();

            IllegalStateException ex = assertThrows(IllegalStateException.class, ReclamoService::new);
            assertTrue(ex.getMessage().contains("No existe reclamos.json"));
        }

        @Test
        @DisplayName("Con un JSON corrupto no se pierden datos ni se lanza excepción")
        void obtenerTodos_jsonCorrupto_listaVacia() {
            DatosDePrueba.escribirTexto(ARCHIVO, "{{{");

            assertTrue(service.obtenerTodos().isEmpty());
        }
    }
}
