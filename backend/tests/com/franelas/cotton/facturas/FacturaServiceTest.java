package com.franelas.cotton.facturas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.franelas.cotton.support.DatosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 1.1.4 - Crear factura.
 * "Como Administrador, quiero crear una factura donde se especifiquen los datos de un cliente
 * registrado, el monto de la compra, los productos comprados y la fecha."
 */
@DisplayName("HU 1.1.4 - Crear factura (FacturaService)")
class FacturaServiceTest {

    private static final String ARCHIVO = "facturas.json";
    private static final TypeReference<List<Factura>> TIPO = new TypeReference<>() {};
    private static final double DELTA = 0.0001;

    private FacturaService service;

    @BeforeEach
    void setUp() {
        DatosDePrueba.limpiar();
        DatosDePrueba.escribir(ARCHIVO, new ArrayList<>());
        service = new FacturaService();
    }

    @AfterEach
    void tearDown() {
        DatosDePrueba.limpiar();
    }

    // ------------------------------------------------------------------ camino feliz
    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Emite una factura válida con estado PENDIENTE y fecha de hoy")
        void registrarFactura_valida_seGuarda() {
            // Arrange
            String codigo = "ORD-AAAA1111";

            // Act
            boolean ok = service.registrarFacturaAgrupada(codigo, "ana@correo.com", 3, 100.0);

            // Assert
            assertTrue(ok);
            List<Factura> facturas = DatosDePrueba.leer(ARCHIVO, TIPO);
            assertEquals(1, facturas.size());
            Factura f = facturas.get(0);
            assertEquals(codigo, f.getCodigoPedido());
            assertEquals("ana@correo.com", f.getClienteCorreo());
            assertEquals(3, f.getCantidadItems());
            assertEquals("PENDIENTE", f.getEstado());
            assertEquals(LocalDate.now().toString(), f.getFechaEmision());
            assertTrue(f.getDescripcion().contains(codigo));
        }

        @ParameterizedTest(name = "subtotal={0} -> IVA={1}, total={2}")
        @CsvSource({
                "100.0,  16.0,  116.0",
                "37.5,   6.0,   43.5",
                "0.01,   0.0016, 0.0116",
                "1250.0, 200.0, 1450.0"
        })
        @DisplayName("Calcula IVA (16%) y total = subtotal + IVA")
        void registrarFactura_calculaIvaYTotal(double subtotal, double ivaEsperado, double totalEsperado) {
            service.registrarFacturaAgrupada("ORD-CALC", "a@a.com", 1, subtotal);

            Factura f = DatosDePrueba.leer(ARCHIVO, TIPO).get(0);
            assertEquals(subtotal, f.getSubtotal(), DELTA);
            assertEquals(ivaEsperado, f.getIva(), DELTA);
            assertEquals(totalEsperado, f.getTotal(), DELTA);
            assertEquals(f.getSubtotal() + f.getIva(), f.getTotal(), DELTA);
            assertTrue(f.getTotal() >= 0, "El total debe ser mayor o igual a cero");
        }

        @Test
        @DisplayName("Asigna números de factura únicos y secuenciales")
        void registrarVariasFacturas_idsSecuenciales() {
            service.registrarFacturaAgrupada("ORD-1", "a@a.com", 1, 10);
            service.registrarFacturaAgrupada("ORD-2", "b@b.com", 1, 20);
            service.registrarFacturaAgrupada("ORD-3", "c@c.com", 1, 30);

            List<Long> ids = DatosDePrueba.leer(ARCHIVO, TIPO).stream().map(Factura::getId).toList();
            assertEquals(List.of(1L, 2L, 3L), ids);
        }

        @Test
        @DisplayName("Indica si un pedido ya tiene factura (sin importar mayúsculas)")
        void existeFacturaParaCodigoPedido() {
            service.registrarFacturaAgrupada("ORD-ABC", "a@a.com", 1, 10);

            assertTrue(service.existeFacturaParaCodigoPedido("ord-abc"));
            assertFalse(service.existeFacturaParaCodigoPedido("ORD-XYZ"));
        }

        @ParameterizedTest(name = "Estado válido: {0}")
        @ValueSource(strings = {"PAGADA", "anulada", "\"PENDIENTE\"", "  pagada  "})
        @DisplayName("Actualiza el estado a un valor permitido (PENDIENTE, PAGADA, ANULADA)")
        void actualizarEstado_valido(String estado) {
            service.registrarFacturaAgrupada("ORD-E", "a@a.com", 1, 10);

            assertTrue(service.actualizarEstado(1, estado));
            String esperado = estado.replace("\"", "").trim().toUpperCase();
            assertEquals(esperado, DatosDePrueba.leer(ARCHIVO, TIPO).get(0).getEstado());
        }

        @Test
        @DisplayName("Elimina una factura existente")
        void eliminarFactura_existente() {
            service.registrarFacturaAgrupada("ORD-D", "a@a.com", 1, 10);

            assertTrue(service.eliminarFactura(1));
            assertTrue(DatosDePrueba.leer(ARCHIVO, TIPO).isEmpty());
        }

        @Test
        @DisplayName("Crea facturas.json vacío si no existe al iniciar el servicio")
        void constructor_creaArchivoSiNoExiste() {
            DatosDePrueba.limpiar();

            new FacturaService();

            assertTrue(DatosDePrueba.existe(ARCHIVO));
            assertTrue(DatosDePrueba.leer(ARCHIVO, TIPO).isEmpty());
        }
    }

    // ------------------------------------------------------------------ casos de falla
    @Nested
    @DisplayName("Casos de falla")
    class CasosDeFalla {

        @Test
        @DisplayName("No emite dos facturas para el mismo pedido (número no repetible)")
        void registrarFactura_pedidoYaFacturado_retornaFalse() {
            service.registrarFacturaAgrupada("ORD-DUP", "a@a.com", 1, 10);

            assertFalse(service.registrarFacturaAgrupada("ord-dup", "a@a.com", 1, 10));
            assertEquals(1, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @ParameterizedTest(name = "Cantidad de ítems inválida: {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("No emite factura si la sección de ítems está vacía")
        void registrarFactura_sinItems_retornaFalse(int cantidad) {
            assertFalse(service.registrarFacturaAgrupada("ORD-X", "a@a.com", cantidad, 10));
            assertTrue(DatosDePrueba.leer(ARCHIVO, TIPO).isEmpty());
        }

        @ParameterizedTest(name = "Subtotal inválido: {0}")
        @ValueSource(doubles = {0.0, -0.01, -100.0})
        @DisplayName("No emite factura con subtotal menor o igual a cero")
        void registrarFactura_subtotalInvalido_retornaFalse(double subtotal) {
            assertFalse(service.registrarFacturaAgrupada("ORD-X", "a@a.com", 1, subtotal));
        }

        @ParameterizedTest(name = "Cliente inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("No emite factura si el cliente no está identificado")
        void registrarFactura_sinCliente_retornaFalse(String cliente) {
            assertFalse(service.registrarFacturaAgrupada("ORD-X", cliente, 1, 10));
        }

        @ParameterizedTest(name = "Código de pedido inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("No emite factura sin código de pedido")
        void registrarFactura_sinCodigo_retornaFalse(String codigo) {
            assertFalse(service.registrarFacturaAgrupada(codigo, "a@a.com", 1, 10));
        }

        @ParameterizedTest(name = "Estado inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"FACTURADO", "CERRADA", "cualquiera"})
        @DisplayName("Rechaza estados no permitidos")
        void actualizarEstado_invalido_retornaFalse(String estado) {
            service.registrarFacturaAgrupada("ORD-E", "a@a.com", 1, 10);

            assertFalse(service.actualizarEstado(1, estado));
            assertEquals("PENDIENTE", DatosDePrueba.leer(ARCHIVO, TIPO).get(0).getEstado());
        }

        @Test
        @DisplayName("No actualiza el estado de una factura inexistente")
        void actualizarEstado_inexistente_retornaFalse() {
            assertFalse(service.actualizarEstado(99, "PAGADA"));
        }

        @Test
        @DisplayName("No elimina una factura inexistente")
        void eliminarFactura_inexistente_retornaFalse() {
            assertFalse(service.eliminarFactura(99));
        }

        @Test
        @DisplayName("Devuelve lista vacía si facturas.json está corrupto")
        void obtenerTodas_jsonCorrupto_listaVacia() {
            DatosDePrueba.escribirTexto(ARCHIVO, "[{]");

            assertTrue(service.obtenerTodasLasFacturas().isEmpty());
        }

        @Test
        @DisplayName("existeFactura con código nulo o vacío devuelve false")
        void existeFactura_codigoVacio_false() {
            assertFalse(service.existeFacturaParaCodigoPedido(null));
            assertFalse(service.existeFacturaParaCodigoPedido(" "));
        }
    }
}
