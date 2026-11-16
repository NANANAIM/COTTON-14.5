package com.franelas.cotton.pedidos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.franelas.cotton.facturas.Factura;
import com.franelas.cotton.inventario.Producto;
import com.franelas.cotton.inventario.ProductoService;
import com.franelas.cotton.support.DatosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * HU 1.1.3 - Realizar pedido.
 * "Como Usuario (Registrado) quiero poder realizar los pedidos de prendas con su respectiva
 * talla y color para registrar mi compra de manera virtual."
 *
 * El inventario (ProductoService) se simula con Mockito para aislar la lógica de pedidos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HU 1.1.3 - Realizar pedido (PedidoService)")
class PedidoServiceTest {

    private static final String PEDIDOS = "pedidos.json";
    private static final String FACTURAS = "facturas.json";
    private static final TypeReference<List<Pedido>> TIPO = new TypeReference<>() {};

    @Mock
    private ProductoService productoService;

    private List<Producto> inventario;
    private PedidoService service;

    @BeforeEach
    void setUp() {
        DatosDePrueba.limpiar();
        DatosDePrueba.escribir(PEDIDOS, new ArrayList<>());
        DatosDePrueba.escribir(FACTURAS, new ArrayList<>());

        inventario = new ArrayList<>(List.of(
                new Producto(1, "Franela", "Negro", "M", 10.0, 20),
                new Producto(2, "Franela", "Blanco", "S", 12.5, 3),
                new Producto(3, "Franela", "Rojo", "XL", 15.0, 50)
        ));
        when(productoService.obtenerTodosLosProductos()).thenReturn(inventario);
        when(productoService.guardarProductos(anyList())).thenReturn(true);

        service = new PedidoService(productoService);
    }

    @AfterEach
    void tearDown() {
        DatosDePrueba.limpiar();
    }

    /** Test Data Builder para líneas de pedido. */
    private static Pedido linea(String usuario, long idProducto, String color, String talla, int cantidad) {
        Pedido p = new Pedido();
        p.setUsuario(usuario);
        p.setIdProducto(idProducto);
        p.setColor(color);
        p.setTalla(talla);
        p.setCantidad(cantidad);
        return p;
    }

    private Producto producto(long id) {
        return inventario.stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ camino feliz
    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Registra un pedido válido y lo almacena en el JSON")
        void registrarPedidoValido_seGuardaEnJson() {
            // Arrange
            List<Pedido> solicitud = List.of(linea("ana@correo.com", 1, "Negro", "M", 2));

            // Act
            boolean ok = service.registrarMultiplesPedidos(solicitud);

            // Assert
            assertTrue(ok);
            List<Pedido> guardados = DatosDePrueba.leer(PEDIDOS, TIPO);
            assertEquals(1, guardados.size());
            Pedido p = guardados.get(0);
            assertEquals("ana@correo.com", p.getUsuario());
            assertEquals("Negro", p.getColor());
            assertEquals("M", p.getTalla());
            assertEquals(2, p.getCantidad());
        }

        @Test
        @DisplayName("Calcula precio unitario y total a partir del inventario")
        void registrarPedido_calculaTotal() {
            service.registrarMultiplesPedidos(List.of(linea("ana@correo.com", 2, "Blanco", "S", 3)));

            Pedido p = DatosDePrueba.leer(PEDIDOS, TIPO).get(0);
            assertEquals(12.5, p.getPrecioUnitario());
            assertEquals(37.5, p.getTotal(), 0.0001);
        }

        @Test
        @DisplayName("Descuenta el stock del inventario y lo guarda")
        void registrarPedido_descuentaStock() {
            service.registrarMultiplesPedidos(List.of(linea("ana@correo.com", 1, "Negro", "M", 5)));

            assertEquals(15, producto(1).getStock());
            verify(productoService).guardarProductos(inventario);
        }

        @Test
        @DisplayName("Todas las líneas de un mismo pedido comparten un código único ORD-XXXXXXXX")
        void registrarVariasLineas_compartenCodigoUnico() {
            service.registrarMultiplesPedidos(List.of(
                    linea("ana@correo.com", 1, "Negro", "M", 1),
                    linea("ana@correo.com", 3, "Rojo", "XL", 2)
            ));

            List<Pedido> guardados = DatosDePrueba.leer(PEDIDOS, TIPO);
            assertEquals(2, guardados.size());
            String codigo = guardados.get(0).getCodigo();
            assertTrue(codigo.matches("ORD-[0-9A-F]{8}"), "Formato de código inesperado: " + codigo);
            assertEquals(codigo, guardados.get(1).getCodigo());
            assertEquals(1, guardados.get(0).getId());
            assertEquals(2, guardados.get(1).getId());
        }

        @Test
        @DisplayName("Dos pedidos distintos reciben códigos distintos e IDs consecutivos")
        void dosPedidos_codigosDistintos() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 1)));
            service.registrarMultiplesPedidos(List.of(linea("b@b.com", 1, "Negro", "M", 1)));

            List<Pedido> guardados = DatosDePrueba.leer(PEDIDOS, TIPO);
            assertNotEquals(guardados.get(0).getCodigo(), guardados.get(1).getCodigo());
            assertEquals(2, guardados.get(1).getId());
        }

        @Test
        @DisplayName("Busca el producto por color y talla si no se envía el ID del producto")
        void registrarPedidoSinIdProducto_buscaPorVariante() {
            service.registrarMultiplesPedidos(List.of(linea("ana@correo.com", 0, "rojo", "xl", 4)));

            Pedido p = DatosDePrueba.leer(PEDIDOS, TIPO).get(0);
            assertEquals(3, p.getIdProducto());
            assertEquals("Rojo", p.getColor());
            assertEquals("XL", p.getTalla());
        }

        @Test
        @DisplayName("Permite exactamente 10 unidades por variante (valor límite)")
        void registrarPedido_diezUnidades_esValido() {
            assertTrue(service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 10))));
        }

        @Test
        @DisplayName("Usa la fecha de hoy si el pedido no trae fecha")
        void registrarPedido_asignaFechaActual() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 1)));

            assertEquals(LocalDate.now().toString(), DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getFecha());
        }

        @Test
        @DisplayName("El usuario puede consultar los pedidos realizados (\"Ver Pedido\")")
        void obtenerTodosLosPedidos_devuelveLosRegistrados() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 1)));

            List<Pedido> pedidos = service.obtenerTodosLosPedidos();

            assertEquals(1, pedidos.size());
            assertEquals("a@a.com", pedidos.get(0).getUsuario());
        }

        @Test
        @DisplayName("Actualiza un pedido no facturado y reajusta el stock")
        void actualizarPedido_noFacturado_reajustaStock() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 4)));
            assertEquals(16, producto(1).getStock());
            Pedido cambio = linea("a@a.com", 1, "Negro", "M", 6);
            cambio.setId(1);

            boolean ok = service.actualizarPedido(cambio);

            assertTrue(ok);
            assertEquals(14, producto(1).getStock());
            assertEquals(6, DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getCantidad());
        }

        @Test
        @DisplayName("Elimina un pedido no facturado y devuelve el stock")
        void eliminarPedido_noFacturado_devuelveStock() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 4)));

            assertTrue(service.eliminarPedido(1));
            assertEquals(20, producto(1).getStock());
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
        }

        @Test
        @DisplayName("Elimina todas las líneas de un pedido por su código")
        void eliminarPorCodigo_noFacturado() {
            service.registrarMultiplesPedidos(List.of(
                    linea("a@a.com", 1, "Negro", "M", 1),
                    linea("a@a.com", 3, "Rojo", "XL", 1)));
            String codigo = DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getCodigo();

            assertTrue(service.eliminarPedidosPorCodigo(codigo));
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
        }
    }

    // ------------------------------------------------------------------ casos de falla
    @Nested
    @DisplayName("Casos de falla")
    class CasosDeFalla {

        @Test
        @DisplayName("Rechaza una lista de pedidos nula o vacía")
        void registrarListaVaciaONula_retornaFalse() {
            assertFalse(service.registrarMultiplesPedidos(null));
            assertFalse(service.registrarMultiplesPedidos(List.of()));
            verifyNoInteractions(productoService);
        }

        @ParameterizedTest(name = "Cantidad inválida: {0}")
        @ValueSource(ints = {0, -1, 11, 50})
        @DisplayName("Rechaza cantidades <= 0 o mayores a 10 unidades por variante")
        void registrarPedido_cantidadInvalida_retornaFalse(int cantidad) {
            boolean ok = service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", cantidad)));

            assertFalse(ok);
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
            verify(productoService, never()).guardarProductos(anyList());
        }

        @Test
        @DisplayName("Rechaza si la misma variante suma más de 10 unidades en varias líneas")
        void registrarPedido_varianteAcumuladaSupera10_retornaFalse() {
            boolean ok = service.registrarMultiplesPedidos(List.of(
                    linea("a@a.com", 1, "Negro", "M", 6),
                    linea("a@a.com", 1, "negro", "m", 5)));

            assertFalse(ok);
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
        }

        @ParameterizedTest(name = "Talla inválida: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"XXL", "XS", "40"})
        @DisplayName("Rechaza tallas personalizadas o fuera del catálogo")
        void registrarPedido_tallaInvalida_retornaFalse(String talla) {
            assertFalse(service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", talla, 1))));
        }

        @ParameterizedTest(name = "Color inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"Fucsia", "Estampado"})
        @DisplayName("Rechaza colores no predefinidos (no hay personalización)")
        void registrarPedido_colorInvalido_retornaFalse(String color) {
            assertFalse(service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, color, "M", 1))));
        }

        @Test
        @DisplayName("Rechaza un producto que no existe en el catálogo")
        void registrarPedido_productoInexistente_retornaFalse() {
            // Morado/L no existe en el inventario de prueba
            boolean ok = service.registrarMultiplesPedidos(List.of(linea("a@a.com", 0, "Morado", "L", 1)));

            assertFalse(ok);
            verify(productoService, never()).guardarProductos(anyList());
        }

        @Test
        @DisplayName("Rechaza el pedido si no hay stock suficiente")
        void registrarPedido_stockInsuficiente_retornaFalse() {
            boolean ok = service.registrarMultiplesPedidos(List.of(linea("a@a.com", 2, "Blanco", "S", 4)));

            assertFalse(ok);
            assertEquals(3, producto(2).getStock());
        }

        @Test
        @DisplayName("Rechaza el pedido si el inventario está vacío")
        void registrarPedido_inventarioVacio_retornaFalse() {
            when(productoService.obtenerTodosLosProductos()).thenReturn(List.of());

            assertFalse(service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 1))));
        }

        @Test
        @DisplayName("Rechaza todo el pedido si una sola línea es inválida (no hay registros parciales)")
        void registrarPedido_unaLineaInvalida_noGuardaNada() {
            boolean ok = service.registrarMultiplesPedidos(List.of(
                    linea("a@a.com", 1, "Negro", "M", 2),
                    linea("a@a.com", 3, "Rojo", "XXL", 1)));

            assertFalse(ok);
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
            verify(productoService, never()).guardarProductos(anyList());
        }

        @Test
        @DisplayName("No guarda el pedido si falla la actualización del inventario")
        void registrarPedido_fallaGuardarInventario_retornaFalse() {
            when(productoService.guardarProductos(anyList())).thenReturn(false);

            assertFalse(service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 1))));
            assertTrue(DatosDePrueba.leer(PEDIDOS, TIPO).isEmpty());
        }

        @Test
        @DisplayName("No permite editar un pedido ya facturado")
        void actualizarPedido_facturado_retornaFalse() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 2)));
            facturarPedido(DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getCodigo());
            Pedido cambio = linea("a@a.com", 1, "Negro", "M", 5);
            cambio.setId(1);

            assertFalse(service.actualizarPedido(cambio));
            assertEquals(2, DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getCantidad());
        }

        @Test
        @DisplayName("No permite eliminar un pedido ya facturado")
        void eliminarPedido_facturado_retornaFalse() {
            service.registrarMultiplesPedidos(List.of(linea("a@a.com", 1, "Negro", "M", 2)));
            String codigo = DatosDePrueba.leer(PEDIDOS, TIPO).get(0).getCodigo();
            facturarPedido(codigo);

            assertFalse(service.eliminarPedido(1));
            assertFalse(service.eliminarPedidosPorCodigo(codigo));
            assertEquals(1, DatosDePrueba.leer(PEDIDOS, TIPO).size());
        }

        @Test
        @DisplayName("No actualiza un pedido inexistente o con datos inválidos")
        void actualizarPedido_invalido_retornaFalse() {
            Pedido inexistente = linea("a@a.com", 1, "Negro", "M", 1);
            inexistente.setId(99);
            Pedido cantidadExcesiva = linea("a@a.com", 1, "Negro", "M", 11);
            cantidadExcesiva.setId(1);

            assertFalse(service.actualizarPedido(null));
            assertFalse(service.actualizarPedido(inexistente));
            assertFalse(service.actualizarPedido(cantidadExcesiva));
        }

        @Test
        @DisplayName("No elimina un pedido o código inexistente")
        void eliminar_inexistente_retornaFalse() {
            assertFalse(service.eliminarPedido(42));
            assertFalse(service.eliminarPedidosPorCodigo("ORD-NOEXISTE"));
            assertFalse(service.eliminarPedidosPorCodigo("  "));
        }

        private void facturarPedido(String codigo) {
            Factura f = new Factura();
            f.setId(1);
            f.setCodigoPedido(codigo);
            f.setClienteCorreo("a@a.com");
            DatosDePrueba.escribir(FACTURAS, List.of(f));
        }
    }
}
