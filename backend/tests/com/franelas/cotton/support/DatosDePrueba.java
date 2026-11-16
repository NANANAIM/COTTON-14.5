package com.franelas.cotton.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utilidad de soporte para las pruebas (patrón Setup/Teardown).
 *
 * Los servicios del backend leen y escriben archivos JSON usando la ruta relativa
 * "src/main/resources/data". Surefire ejecuta las pruebas con el directorio de trabajo
 * en target/test-workdir, por lo que esa ruta relativa apunta a una carpeta aislada
 * y los datos reales del proyecto nunca se modifican.
 */
public final class DatosDePrueba {

    public static final Path CARPETA_DATOS = Paths.get("src/main/resources/data");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatosDePrueba() {}

    /**
     * Protección: verifica que el directorio de trabajo sea el aislado (target/test-workdir).
     * Si las pruebas se lanzan desde un IDE con el directorio del proyecto como cwd,
     * se abortan para no borrar ni sobrescribir los datos reales.
     */
    private static void verificarDirectorioAislado() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (!cwd.endsWith(Paths.get("target", "test-workdir"))) {
            throw new IllegalStateException(
                    "Las pruebas deben ejecutarse con el directorio de trabajo target/test-workdir "
                            + "(use 'mvn test'). Directorio actual: " + cwd);
        }
    }

    /** Elimina todos los JSON de la carpeta aislada y la deja vacía y lista para usarse. */
    public static void limpiar() {
        verificarDirectorioAislado();
        try {
            Files.createDirectories(CARPETA_DATOS);
            try (Stream<Path> archivos = Files.list(CARPETA_DATOS)) {
                for (Path p : archivos.toList()) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo limpiar la carpeta de datos de prueba", e);
        }
    }

    /** Escribe una lista de objetos como JSON en la carpeta aislada. */
    public static void escribir(String archivo, Object contenido) {
        verificarDirectorioAislado();
        try {
            Files.createDirectories(CARPETA_DATOS);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(CARPETA_DATOS.resolve(archivo).toFile(), contenido);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + archivo, e);
        }
    }

    /** Escribe texto crudo (útil para simular archivos corruptos). */
    public static void escribirTexto(String archivo, String texto) {
        verificarDirectorioAislado();
        try {
            Files.createDirectories(CARPETA_DATOS);
            Files.writeString(CARPETA_DATOS.resolve(archivo), texto);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + archivo, e);
        }
    }

    /** Lee un JSON de la carpeta aislada como lista tipada. */
    public static <T> List<T> leer(String archivo, TypeReference<List<T>> tipo) {
        try {
            return MAPPER.readValue(CARPETA_DATOS.resolve(archivo).toFile(), tipo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }

    public static boolean existe(String archivo) {
        return Files.exists(CARPETA_DATOS.resolve(archivo));
    }
}
