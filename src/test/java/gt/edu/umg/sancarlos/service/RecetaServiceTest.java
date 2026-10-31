package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.RecetaDAO;
import gt.edu.umg.sancarlos.dao.RecetaDAO.ProductoReceta;
import gt.edu.umg.sancarlos.dto.NuevaRecetaDTO;
import gt.edu.umg.sancarlos.dto.NuevoIngredienteDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.dto.RecetaVersionDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.RecetaConflictoException;
import gt.edu.umg.sancarlos.exception.RecetaNoEncontradaException;
import gt.edu.umg.sancarlos.exception.RecetaSolicitudInvalidaException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Versiones de receta sin Oracle: DAO y conexion falsos. */
class RecetaServiceTest {

    private final List<String> eventos = new ArrayList<>();
    private final RecetaDAOFalso dao = new RecetaDAOFalso();

    @Test
    void creaLaSiguienteVersionYCierraLasActivasEnUnaTransaccion() {
        dao.siguienteVersion = 4;

        RecetaDetalleDTO creada = servicio().crear(solicitud(ingrediente(2, "1.5"),
                ingrediente(3, "0.250")));

        assertEquals(70, creada.idReceta());
        assertEquals(List.of("abrir", "autoCommit:false", "bloquear:33", "insumos:[2, 3]",
                "version:33", "desactivar:33", "insertar:33:4:12", "ingredientes:70:2",
                "leer:70", "commit", "close"), eventos);
    }

    @Test
    void sinVersionesPreviasCreaLaVersionUno() {
        dao.siguienteVersion = 1;

        servicio().crear(solicitud(ingrediente(2, "1")));

        assertTrue(eventos.contains("insertar:33:1:12"), eventos.toString());
    }

    @Test
    void otraVersionGuardadaDespuesDeAbrirElFormularioEsConflicto() {
        dao.siguienteVersion = 5;

        RecetaConflictoException error = assertThrows(RecetaConflictoException.class,
                () -> servicio().crear(new NuevaRecetaDTO(33L, new BigDecimal("12"),
                        List.of(ingrediente(2, "1")), 3)));

        assertTrue(error.getMessage().contains("ahora es la versión 4"), error.getMessage());
        assertEquals(List.of("abrir", "autoCommit:false", "bloquear:33", "insumos:[2]",
                "version:33", "rollback", "close"), eventos);
    }

    @Test
    void sinVersionBaseYaHabiendoVersionesEsConflicto() {
        dao.siguienteVersion = 2;

        assertThrows(RecetaConflictoException.class, () -> servicio().crear(
                new NuevaRecetaDTO(33L, new BigDecimal("12"), List.of(ingrediente(2, "1")), null)));
        assertFalse(eventos.contains("desactivar:33"), eventos.toString());
    }

    @Test
    void fallaUnIngredienteYRevierteTodo() {
        dao.errorIngredientes = new SQLException("ORA-02291 detalle secreto", "23000", 2291);

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> servicio().crear(solicitud(ingrediente(2, "1"))));

        assertFalse(error.getMessage().contains("ORA-"));
        assertEquals(List.of("abrir", "autoCommit:false", "bloquear:33", "insumos:[2]",
                "version:33", "desactivar:33", "insertar:33:3:12", "ingredientes:70:1",
                "rollback", "close"), eventos);
    }

    @Test
    void versionDuplicadaPorGuardadoSimultaneoEsConflicto() {
        dao.errorInsertar = new SQLException("ORA-00001 detalle secreto", "23000", 1);

        RecetaConflictoException error = assertThrows(RecetaConflictoException.class,
                () -> servicio().crear(solicitud(ingrediente(2, "1"))));

        assertEquals("Otro usuario guardó una versión de esta receta al mismo tiempo; "
                + "intente de nuevo", error.getMessage());
        assertTrue(eventos.contains("rollback"));
        assertFalse(eventos.contains("commit"));
    }

    @Test
    void productoInexistenteEs404YNoInserta() {
        dao.productos.remove(33L);

        assertThrows(RecetaNoEncontradaException.class,
                () -> servicio().crear(solicitud(ingrediente(2, "1"))));
        assertEquals(List.of("abrir", "autoCommit:false", "bloquear:33", "rollback", "close"),
                eventos);
    }

    @Test
    void soloLosProductosFinalesActivosLlevanReceta() {
        for (ProductoReceta producto : List.of(
                new ProductoReceta(33, "Harina", "INSUMO", "ACTIVO"),
                new ProductoReceta(33, "Pan viejo", "PRODUCTO_FINAL", "INACTIVO"))) {
            dao.productos.put(33L, producto);
            RecetaConflictoException error = assertThrows(RecetaConflictoException.class,
                    () -> servicio().crear(solicitud(ingrediente(2, "1"))));
            assertEquals("Solo los productos finales activos llevan receta", error.getMessage());
        }
        assertFalse(eventos.contains("commit"));
        assertFalse(eventos.stream().anyMatch(e -> e.startsWith("insertar")));
    }

    @Test
    void cadaIngredienteDebeSerUnInsumoActivoExistente() {
        RecetaSolicitudInvalidaException inexistente = assertThrows(
                RecetaSolicitudInvalidaException.class,
                () -> servicio().crear(solicitud(ingrediente(2, "1"), ingrediente(99, "1"))));
        assertEquals("El ingrediente #99 no existe", inexistente.getMessage());

        dao.productos.put(2L, new ProductoReceta(2, "Harina", "INSUMO", "INACTIVO"));
        RecetaConflictoException inactivo = assertThrows(RecetaConflictoException.class,
                () -> servicio().crear(solicitud(ingrediente(2, "1"))));
        assertEquals("El ingrediente \"Harina\" no es un insumo activo", inactivo.getMessage());

        RecetaConflictoException productoFinal = assertThrows(RecetaConflictoException.class,
                () -> servicio().crear(solicitud(ingrediente(33, "1"))));
        assertEquals("El ingrediente \"Pan\" no es un insumo activo", productoFinal.getMessage());

        assertFalse(eventos.contains("commit"));
        assertFalse(eventos.stream().anyMatch(e -> e.startsWith("insertar")));
    }

    @Test
    void validaLaSolicitudSinAbrirConexion() {
        Map<String, NuevaRecetaDTO> casos = new java.util.LinkedHashMap<>();
        casos.put("Los datos de la receta son obligatorios", null);
        casos.put("El ID del producto debe ser positivo",
                new NuevaRecetaDTO(null, BigDecimal.ONE, List.of(ingrediente(2, "1"))));
        casos.put("El ID del producto debe ser positivo ",
                new NuevaRecetaDTO(0L, BigDecimal.ONE, List.of(ingrediente(2, "1"))));
        casos.put("El rendimiento es obligatorio",
                new NuevaRecetaDTO(33L, null, List.of(ingrediente(2, "1"))));
        casos.put("El rendimiento debe ser mayor que cero",
                new NuevaRecetaDTO(33L, BigDecimal.ZERO, List.of(ingrediente(2, "1"))));
        casos.put("El rendimiento admite como máximo 3 decimales",
                new NuevaRecetaDTO(33L, new BigDecimal("1.0001"), List.of(ingrediente(2, "1"))));
        casos.put("El rendimiento no puede superar 999999999.999",
                new NuevaRecetaDTO(33L, new BigDecimal("1000000000"), List.of(ingrediente(2, "1"))));
        casos.put("La receta debe tener al menos un ingrediente",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of()));
        casos.put("La receta debe tener al menos un ingrediente ",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, null));
        List<NuevoIngredienteDTO> muchos = new ArrayList<>();
        for (long id = 1; id <= 51; id++) {
            muchos.add(ingrediente(id, "1"));
        }
        casos.put("La receta no puede tener más de 50 ingredientes",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, muchos));
        casos.put("El ID de cada ingrediente debe ser positivo",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of(ingrediente(0, "1"))));
        casos.put("El ID de cada ingrediente debe ser positivo ",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, Collections.singletonList(null)));
        casos.put("La cantidad de cada ingrediente es obligatoria",
                new NuevaRecetaDTO(33L, BigDecimal.ONE,
                        List.of(new NuevoIngredienteDTO(2L, null))));
        casos.put("La cantidad de cada ingrediente debe ser mayor que cero",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of(ingrediente(2, "-1"))));
        casos.put("La cantidad de cada ingrediente admite como máximo 3 decimales",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of(ingrediente(2, "0.0005"))));
        casos.put("La cantidad de cada ingrediente no puede superar 999999999.999",
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of(ingrediente(2, "1000000000.000"))));
        casos.put("El ingrediente #2 está repetido",
                new NuevaRecetaDTO(33L, BigDecimal.ONE,
                        List.of(ingrediente(2, "1"), ingrediente(2, "2"))));

        for (Map.Entry<String, NuevaRecetaDTO> caso : casos.entrySet()) {
            RecetaSolicitudInvalidaException error = assertThrows(
                    RecetaSolicitudInvalidaException.class,
                    () -> servicio().crear(caso.getValue()), caso.getKey());
            assertEquals(caso.getKey().trim(), error.getMessage());
        }
        assertTrue(eventos.isEmpty(), eventos.toString());
    }

    @Test
    void aceptaLosLimitesDeNumber12Coma3() {
        RecetaService.validar(new NuevaRecetaDTO(33L, new BigDecimal("999999999.999"),
                List.of(ingrediente(2, "0.001"), ingrediente(3, "1.5000"))));
        List<NuevoIngredienteDTO> cincuenta = new ArrayList<>();
        for (long id = 1; id <= 50; id++) {
            cincuenta.add(ingrediente(id, "1"));
        }
        RecetaService.validar(new NuevaRecetaDTO(33L, BigDecimal.ONE, cincuenta));
    }

    @Test
    void detalleYVersionesInexistentesSon404() {
        assertThrows(RecetaNoEncontradaException.class, () -> servicio().buscarPorId(5));
        assertThrows(RecetaNoEncontradaException.class, () -> servicio().listarVersiones(404));
        assertThrows(RecetaSolicitudInvalidaException.class, () -> servicio().buscarPorId(0));
        assertThrows(RecetaSolicitudInvalidaException.class, () -> servicio().listarVersiones(-1));
    }

    @Test
    void listaLasVersionesDeUnProductoExistente() {
        List<RecetaVersionDTO> versiones = servicio().listarVersiones(33);

        assertEquals(2, versiones.size());
        assertEquals(List.of("abrir", "producto:33", "versiones:33", "close"), eventos);
    }

    @Test
    void traduceSoloLaVersionDuplicadaComoConflicto() {
        assertInstanceOf(RecetaConflictoException.class, RecetaService.traducirErrorOracle(
                new SQLException("ORA-00001 detalle secreto", "23000", 1)));
        RuntimeException otro = RecetaService.traducirErrorOracle(
                new SQLException("ORA-02291 detalle secreto", "23000", 2291));
        assertInstanceOf(DataAccessException.class, otro);
        assertFalse(otro.getMessage().contains("ORA-"));
    }

    private RecetaService servicio() {
        return new RecetaService(dao, this::conexion);
    }

    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> eventos.add("autoCommit:" + args[0]);
                        case "commit", "rollback", "close" -> eventos.add(method.getName());
                        default -> { }
                    }
                    return null;
                });
    }

    /** Basada en la ultima version que tiene el DAO falso, como lo haria la pantalla. */
    private NuevaRecetaDTO solicitud(NuevoIngredienteDTO... ingredientes) {
        int ultima = dao.siguienteVersion - 1;
        return new NuevaRecetaDTO(33L, new BigDecimal("12"), List.of(ingredientes),
                ultima == 0 ? null : ultima);
    }

    private static NuevoIngredienteDTO ingrediente(long id, String cantidad) {
        return new NuevoIngredienteDTO(id, new BigDecimal(cantidad));
    }

    private final class RecetaDAOFalso extends RecetaDAO {
        final Map<Long, ProductoReceta> productos = new HashMap<>(Map.of(
                33L, new ProductoReceta(33, "Pan", "PRODUCTO_FINAL", "ACTIVO"),
                2L, new ProductoReceta(2, "Harina", "INSUMO", "ACTIVO"),
                3L, new ProductoReceta(3, "Azúcar", "INSUMO", "ACTIVO")));
        int siguienteVersion = 3;
        SQLException errorInsertar;
        SQLException errorIngredientes;

        @Override public Optional<ProductoReceta> bloquearProducto(Connection connection,
                long idProducto) {
            eventos.add("bloquear:" + idProducto);
            return Optional.ofNullable(productos.get(idProducto));
        }

        @Override public Optional<ProductoReceta> buscarProducto(Connection connection,
                long idProducto) {
            eventos.add("producto:" + idProducto);
            return Optional.ofNullable(productos.get(idProducto));
        }

        @Override public Map<Long, ProductoReceta> buscarProductos(Connection connection,
                Collection<Long> ids) {
            eventos.add("insumos:" + ids);
            Map<Long, ProductoReceta> encontrados = new HashMap<>();
            for (Long id : ids) {
                if (productos.containsKey(id)) encontrados.put(id, productos.get(id));
            }
            return encontrados;
        }

        @Override public int siguienteVersion(Connection connection, long idProducto) {
            eventos.add("version:" + idProducto);
            return siguienteVersion;
        }

        @Override public int desactivarActivas(Connection connection, long idProducto) {
            eventos.add("desactivar:" + idProducto);
            return 1;
        }

        @Override public long insertarReceta(Connection connection, long idProducto,
                int version, BigDecimal rendimiento) throws SQLException {
            eventos.add("insertar:" + idProducto + ":" + version + ":" + rendimiento);
            if (errorInsertar != null) throw errorInsertar;
            return 70;
        }

        @Override public void insertarIngredientes(Connection connection, long idReceta,
                List<NuevoIngredienteDTO> ingredientes) throws SQLException {
            eventos.add("ingredientes:" + idReceta + ":" + ingredientes.size());
            if (errorIngredientes != null) throw errorIngredientes;
        }

        @Override public Optional<RecetaDetalleDTO> buscarDetalle(Connection connection,
                long idReceta) {
            if (idReceta != 70) return Optional.empty();
            eventos.add("leer:" + idReceta);
            return Optional.of(new RecetaDetalleDTO(idReceta, 33, "Pan", "UND",
                    siguienteVersion, new BigDecimal("12"), "ACTIVA",
                    LocalDate.of(2026, 10, 31), null, true, List.of()));
        }

        @Override public List<RecetaVersionDTO> listarVersiones(Connection connection,
                long idProducto) {
            eventos.add("versiones:" + idProducto);
            return List.of(
                    new RecetaVersionDTO(71, 2, BigDecimal.TEN, "ACTIVA",
                            LocalDate.of(2026, 10, 31), null, true, 3),
                    new RecetaVersionDTO(70, 1, BigDecimal.TEN, "INACTIVA",
                            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), false, 2));
        }
    }
}
