package com.franelas.cotton.inventario;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 1.1.1 - Registrar productos.
 * "Como Administrador, quiero registrar nuevos productos en el sistema especificando
 * el color de prenda y talla, para mantener actualizado el catálogo."
 */
@DisplayName("HU 1.1.1 - Registrar productos (ProductoService)")
class ProductoServiceTest {

    private static final String ARCHIVO = "productos.json";
    private static final TypeReference<List<Producto>> TIPO = new TypeReference<>() {};

    private ProductoService service;

    @BeforeEach
    void setUp() {
        DatosDePrueba.limpiar();
        DatosDePrueba.escribir(ARCHIVO, List.of(
                new Producto(1, "Franela básica", "Negro", "M", 10.0, 20),
                new Producto(2, "Franela básica", "Blanco", "S", 12.5, 5)
        ));
        service = new ProductoService();
    }

    @AfterEach
    void tearDown() {
        DatosDePrueba.limpiar();
    }

    private Producto productoValido() {
        return new Producto(0, "Franela unisex", "Rojo", "L", 15.0, 8);
    }

    // ------------------------------------------------------------------ camino feliz
    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("Registra un producto válido y lo guarda en el JSON")
        void registrarProductoValido_loPersisteEnJson() {
            // Arrange
            Producto nuevo = productoValido();

            // Act
            boolean resultado = service.registrarProducto(nuevo);

            // Assert
            assertTrue(resultado);
            List<Producto> guardados = DatosDePrueba.leer(ARCHIVO, TIPO);
            assertEquals(3, guardados.size());
            Producto ultimo = guardados.get(2);
            assertEquals("Rojo", ultimo.getColor());
            assertEquals("L", ultimo.getTalla());
            assertEquals(15.0, ultimo.getPrecio());
            assertEquals(8, ultimo.getStock());
        }

        @Test
        @DisplayName("Asigna un ID secuencial (máximo + 1) cuando el producto llega sin ID")
        void registrarProductoSinId_asignaIdSecuencial() {
            Producto nuevo = productoValido();

            service.registrarProducto(nuevo);

            assertEquals(3, nuevo.getId());
        }

        @Test
        @DisplayName("Normaliza talla a mayúsculas y color al formato del catálogo")
        void registrarProducto_normalizaTallaYColor() {
            Producto nuevo = new Producto(0, "Franela", "aZuL", "xl", 9.99, 1);

            boolean resultado = service.registrarProducto(nuevo);

            assertTrue(resultado);
            assertEquals("Azul", nuevo.getColor());
            assertEquals("XL", nuevo.getTalla());
        }

        @ParameterizedTest(name = "Talla válida: {0}")
        @ValueSource(strings = {"S", "M", "L", "XL"})
        @DisplayName("Acepta todas las tallas permitidas (S, M, L, XL)")
        void registrarProducto_aceptaTallasPermitidas(String talla) {
            Producto nuevo = new Producto(0, "Franela", "Verde", talla, 10, 1);

            assertTrue(service.registrarProducto(nuevo));
        }

        @ParameterizedTest(name = "Color válido: {0}")
        @ValueSource(strings = {"Blanco", "Negro", "Rojo", "Azul", "Amarillo", "Verde", "Morado"})
        @DisplayName("Acepta todos los colores sólidos del catálogo")
        void registrarProducto_aceptaColoresDelCatalogo(String color) {
            Producto nuevo = new Producto(0, "Franela", color, "M", 10, 1);

            assertTrue(service.registrarProducto(nuevo));
        }

        @Test
        @DisplayName("Acepta stock en cero (producto agotado pero registrado)")
        void registrarProducto_stockCeroEsValido() {
            Producto nuevo = new Producto(0, "Franela", "Negro", "S", 10, 0);

            assertTrue(service.registrarProducto(nuevo));
        }

        @Test
        @DisplayName("Crea el archivo si no existe y registra el primer producto con ID 1")
        void registrarProducto_sinArchivo_creaListaNueva() {
            DatosDePrueba.limpiar();
            Producto nuevo = productoValido();

            boolean resultado = service.registrarProducto(nuevo);

            assertTrue(resultado);
            assertEquals(1, nuevo.getId());
            assertEquals(1, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("Consulta todos los productos del catálogo")
        void obtenerTodosLosProductos_devuelveCatalogo() {
            List<Producto> productos = service.obtenerTodosLosProductos();

            assertEquals(2, productos.size());
            assertEquals("Negro", productos.get(0).getColor());
        }

        @Test
        @DisplayName("Edita un producto existente")
        void editarProducto_existente_actualizaDatos() {
            Producto editado = new Producto(1, "Franela premium", "Morado", "XL", 20.0, 3);

            boolean resultado = service.editarProducto(editado);

            assertTrue(resultado);
            Producto guardado = DatosDePrueba.leer(ARCHIVO, TIPO).get(0);
            assertEquals("Morado", guardado.getColor());
            assertEquals("XL", guardado.getTalla());
            assertEquals(20.0, guardado.getPrecio());
        }

        @Test
        @DisplayName("Elimina un producto existente")
        void eliminarProducto_existente_loQuitaDelJson() {
            boolean resultado = service.eliminarProducto(2);

            assertTrue(resultado);
            List<Producto> restantes = DatosDePrueba.leer(ARCHIVO, TIPO);
            assertEquals(1, restantes.size());
            assertEquals(1, restantes.get(0).getId());
        }

        @Test
        @DisplayName("Corrige IDs inválidos o duplicados al leer el catálogo")
        void obtenerTodos_normalizaIdsDuplicados() {
            DatosDePrueba.escribir(ARCHIVO, List.of(
                    new Producto(1, "A", "Negro", "M", 10, 1),
                    new Producto(1, "B", "Rojo", "S", 10, 1),
                    new Producto(0, "C", "Azul", "L", 10, 1)
            ));

            List<Producto> productos = service.obtenerTodosLosProductos();

            assertEquals(List.of(1L, 2L, 3L), productos.stream().map(Producto::getId).toList());
        }
    }

    // ------------------------------------------------------------------ casos de falla
    @Nested
    @DisplayName("Casos de falla")
    class CasosDeFalla {

        @Test
        @DisplayName("Rechaza un producto nulo")
        void registrarProductoNulo_retornaFalse() {
            assertFalse(service.registrarProducto(null));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @ParameterizedTest(name = "Precio inválido: {0}")
        @ValueSource(doubles = {0.0, -1.0, -150.75})
        @DisplayName("Rechaza precios menores o iguales a cero")
        void registrarProducto_precioInvalido_retornaFalse(double precio) {
            Producto nuevo = new Producto(0, "Franela", "Negro", "M", precio, 1);

            assertFalse(service.registrarProducto(nuevo));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("Rechaza stock negativo")
        void registrarProducto_stockNegativo_retornaFalse() {
            Producto nuevo = new Producto(0, "Franela", "Negro", "M", 10, -1);

            assertFalse(service.registrarProducto(nuevo));
        }

        @ParameterizedTest(name = "Talla inválida: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"XS", "XXL", "X", "38", "Mediana"})
        @DisplayName("Rechaza tallas fuera de S, M, L, XL (no hay tallas personalizadas)")
        void registrarProducto_tallaInvalida_retornaFalse(String talla) {
            Producto nuevo = new Producto(0, "Franela", "Negro", talla, 10, 1);

            assertFalse(service.registrarProducto(nuevo));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @ParameterizedTest(name = "Color inválido: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"Rosado", "Gris", "Negro con rayas", "Multicolor"})
        @DisplayName("Rechaza colores que no son sólidos del catálogo")
        void registrarProducto_colorInvalido_retornaFalse(String color) {
            Producto nuevo = new Producto(0, "Franela", color, "M", 10, 1);

            assertFalse(service.registrarProducto(nuevo));
        }

        @Test
        @DisplayName("Rechaza un archivo JSON corrupto sin perder la información")
        void registrarProducto_jsonCorrupto_retornaFalse() {
            DatosDePrueba.escribirTexto(ARCHIVO, "{ esto no es json valido ");

            assertFalse(service.registrarProducto(productoValido()));
        }

        @Test
        @DisplayName("Devuelve lista vacía si el JSON está corrupto")
        void obtenerTodos_jsonCorrupto_listaVacia() {
            DatosDePrueba.escribirTexto(ARCHIVO, "[{\"id\": ");

            assertTrue(service.obtenerTodosLosProductos().isEmpty());
        }

        @Test
        @DisplayName("Devuelve lista vacía si no existe el archivo de productos")
        void obtenerTodos_sinArchivo_listaVacia() {
            DatosDePrueba.limpiar();

            assertTrue(service.obtenerTodosLosProductos().isEmpty());
        }

        @ParameterizedTest(name = "ID {0} -> eliminar falla")
        @CsvSource({"0", "-5", "999"})
        @DisplayName("No elimina con ID inválido o inexistente")
        void eliminarProducto_idInvalido_retornaFalse(long id) {
            assertFalse(service.eliminarProducto(id));
            assertEquals(2, DatosDePrueba.leer(ARCHIVO, TIPO).size());
        }

        @Test
        @DisplayName("No edita un producto inexistente")
        void editarProducto_inexistente_retornaFalse() {
            Producto editado = new Producto(999, "Fantasma", "Negro", "M", 10, 1);

            assertFalse(service.editarProducto(editado));
        }

        @Test
        @DisplayName("No edita si los nuevos datos son inválidos")
        void editarProducto_datosInvalidos_noModificaJson() {
            Producto editado = new Producto(1, "Franela", "Negro", "XXL", 10, 1);

            assertFalse(service.editarProducto(editado));
            assertEquals("M", DatosDePrueba.leer(ARCHIVO, TIPO).get(0).getTalla());
        }

        @Test
        @DisplayName("No edita un producto nulo o sin ID")
        void editarProducto_nuloOSinId_retornaFalse() {
            assertFalse(service.editarProducto(null));
            assertFalse(service.editarProducto(productoValido()));
        }
    }
}
