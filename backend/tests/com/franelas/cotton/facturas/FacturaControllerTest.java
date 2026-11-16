package com.franelas.cotton.facturas;

import com.franelas.cotton.pedidos.Pedido;
import com.franelas.cotton.pedidos.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HU 1.1.4 - Crear factura: el controlador agrupa todas las líneas de un pedido
 * (múltiples líneas de productos en una sola factura). Servicios simulados con Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU 1.1.4 - Crear factura (FacturaController con Mockito)")
class FacturaControllerTest {

    @Mock
    private FacturaService facturaService;

    @Mock
    private PedidoService pedidoService;

    private FacturaController controller;

    @BeforeEach
    void setUp() {
        controller = new FacturaController(facturaService, pedidoService);
    }

    private static Pedido linea(long id, String codigo, int cantidad, double total) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setCodigo(codigo);
        p.setUsuario("ana@correo.com");
        p.setCantidad(cantidad);
        p.setTotal(total);
        return p;
    }

    private static Map<String, Object> payload(Object id) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        return m;
    }

    // ------------------------------------------------------------------ camino feliz

    @Test
    @DisplayName("Agrupa todas las líneas del pedido: suma unidades y subtotales en una sola factura")
    void crearFactura_agrupaLineasDelMismoPedido() {
        // Arrange
        when(pedidoService.obtenerTodosLosPedidos()).thenReturn(List.of(
                linea(1, "ORD-A", 2, 20.0),
                linea(2, "ORD-A", 3, 45.0),
                linea(3, "ORD-B", 1, 99.0)   // otro pedido: no debe sumarse
        ));
        when(facturaService.existeFacturaParaCodigoPedido("ORD-A")).thenReturn(false);
        when(facturaService.registrarFacturaAgrupada(anyString(), anyString(), anyInt(), anyDouble())).thenReturn(true);

        // Act
        boolean ok = controller.crearFactura(payload(2));

        // Assert
        assertTrue(ok);
        verify(facturaService).registrarFacturaAgrupada("ORD-A", "ana@correo.com", 5, 65.0);
    }

    @Test
    @DisplayName("Acepta el ID del pedido como texto en el payload")
    void crearFactura_idComoTexto() {
        when(pedidoService.obtenerTodosLosPedidos()).thenReturn(List.of(linea(7, "ORD-Z", 1, 10.0)));
        when(facturaService.registrarFacturaAgrupada(anyString(), anyString(), anyInt(), anyDouble())).thenReturn(true);

        assertTrue(controller.crearFactura(payload("7")));
    }

    // ------------------------------------------------------------------ casos de falla

    @Test
    @DisplayName("No factura si el payload no trae ID de pedido")
    void crearFactura_sinId_retornaFalse() {
        assertFalse(controller.crearFactura(new HashMap<>()));
        verifyNoInteractions(pedidoService, facturaService);
    }

    @Test
    @DisplayName("No factura si el ID no es numérico")
    void crearFactura_idNoNumerico_retornaFalse() {
        assertFalse(controller.crearFactura(payload("abc")));
        verifyNoInteractions(facturaService);
    }

    @Test
    @DisplayName("No factura si el pedido referenciado no existe")
    void crearFactura_pedidoInexistente_retornaFalse() {
        when(pedidoService.obtenerTodosLosPedidos()).thenReturn(List.of(linea(1, "ORD-A", 1, 10)));

        assertFalse(controller.crearFactura(payload(99)));
        verify(facturaService, never()).registrarFacturaAgrupada(any(), any(), anyInt(), anyDouble());
    }

    @Test
    @DisplayName("No factura si el pedido no tiene código")
    void crearFactura_pedidoSinCodigo_retornaFalse() {
        when(pedidoService.obtenerTodosLosPedidos()).thenReturn(List.of(linea(1, null, 1, 10)));

        assertFalse(controller.crearFactura(payload(1)));
        verifyNoInteractions(facturaService);
    }

    @Test
    @DisplayName("No emite una segunda factura para un pedido ya facturado")
    void crearFactura_yaFacturado_retornaFalse() {
        when(pedidoService.obtenerTodosLosPedidos()).thenReturn(List.of(linea(1, "ORD-A", 1, 10)));
        when(facturaService.existeFacturaParaCodigoPedido("ORD-A")).thenReturn(true);

        assertFalse(controller.crearFactura(payload(1)));
        verify(facturaService, never()).registrarFacturaAgrupada(any(), any(), anyInt(), anyDouble());
    }
}
