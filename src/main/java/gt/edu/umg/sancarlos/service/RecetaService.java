package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.RecetaDAO;
import gt.edu.umg.sancarlos.dao.RecetaDAO.ProductoReceta;
import gt.edu.umg.sancarlos.dto.NuevaRecetaDTO;
import gt.edu.umg.sancarlos.dto.NuevoIngredienteDTO;
import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.dto.RecetaResumenDTO;
import gt.edu.umg.sancarlos.dto.RecetaVersionDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.RecetaAmbiguaException;
import gt.edu.umg.sancarlos.exception.RecetaConflictoException;
import gt.edu.umg.sancarlos.exception.RecetaNoEncontradaException;
import gt.edu.umg.sancarlos.exception.RecetaSolicitudInvalidaException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consulta recetas y guarda nuevas versiones. Una receta nunca se modifica:
 * PRODUCCION_DETALLE apunta a la version usada, asi que cambiarla es crear otra.
 */
public class RecetaService {

    static final int MAXIMO_INGREDIENTES = 50;
    private static final BigDecimal MAXIMO_NUMERO = new BigDecimal("999999999.999");
    private static final int DECIMALES = 3;

    private final RecetaDAO recetaDAO;
    private final ProveedorConexion conexiones;

    @FunctionalInterface
    interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    public RecetaService() {
        this(new RecetaDAO());
    }

    RecetaService(RecetaDAO recetaDAO) {
        this(recetaDAO, ConnectionFactory::getConnection);
    }

    RecetaService(RecetaDAO recetaDAO, ProveedorConexion conexiones) {
        this.recetaDAO = recetaDAO;
        this.conexiones = conexiones;
    }

    public RecetaActivaDTO buscarRecetaActiva(long idProducto) {
        if (idProducto <= 0) {
            throw new IllegalArgumentException("El ID del producto debe ser positivo");
        }

        try (Connection connection = conexiones.abrir()) {
            long idReceta = recetaDAO.buscarIdRecetaActiva(connection, idProducto);
            return new RecetaActivaDTO(idProducto, idReceta);
        } catch (SQLException exception) {
            int codigoOracle = Math.abs(exception.getErrorCode());
            if (codigoOracle == 20201) {
                throw new RecetaNoEncontradaException(
                        "El producto no posee una receta activa", exception);
            }
            if (codigoOracle == 20202) {
                throw new RecetaAmbiguaException(
                        "El producto posee más de una receta activa", exception);
            }
            throw new DataAccessException(
                    "No fue posible consultar la receta activa del producto", exception);
        }
    }

    /** Productos finales activos con su receta vigente; el catalogo es pequeno. */
    public List<RecetaResumenDTO> listar() {
        try (Connection connection = conexiones.abrir()) {
            return recetaDAO.listarResumen(connection);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible listar las recetas", exception);
        }
    }

    public RecetaDetalleDTO buscarPorId(long idReceta) {
        validarId(idReceta, "El ID de la receta debe ser positivo");
        try (Connection connection = conexiones.abrir()) {
            return recetaDAO.buscarDetalle(connection, idReceta)
                    .orElseThrow(() -> new RecetaNoEncontradaException(
                            "No existe una receta con ID " + idReceta, null));
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar la receta", exception);
        }
    }

    public List<RecetaVersionDTO> listarVersiones(long idProducto) {
        validarId(idProducto, "El ID del producto debe ser positivo");
        try (Connection connection = conexiones.abrir()) {
            if (recetaDAO.buscarProducto(connection, idProducto).isEmpty()) {
                throw productoNoEncontrado(idProducto);
            }
            return recetaDAO.listarVersiones(connection, idProducto);
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No fue posible consultar las versiones de la receta", exception);
        }
    }

    /**
     * Guarda la siguiente version y cierra las activas anteriores en la misma
     * transaccion. El bloqueo del producto impide dos versiones activas a la vez.
     */
    public RecetaDetalleDTO crear(NuevaRecetaDTO solicitud) {
        validar(solicitud);
        long idProducto = solicitud.idProducto();
        return transaccion(connection -> {
            ProductoReceta producto = recetaDAO.bloquearProducto(connection, idProducto)
                    .orElseThrow(() -> productoNoEncontrado(idProducto));
            if (!"PRODUCTO_FINAL".equals(producto.tipo()) || !"ACTIVO".equals(producto.estado())) {
                throw new RecetaConflictoException(
                        "Solo los productos finales activos llevan receta", null);
            }
            validarInsumos(connection, solicitud.ingredientes());
            int version = recetaDAO.siguienteVersion(connection, idProducto);
            // Con el producto bloqueado: si alguien guardo otra version despues de
            // que se abrio el formulario, se rechaza en lugar de reemplazarla.
            int base = solicitud.versionBase() == null ? 0 : solicitud.versionBase();
            if (version != base + 1) {
                throw new RecetaConflictoException("La receta cambió mientras la editaba (ahora es la versión "
                        + (version - 1) + "). Revise la versión actual y vuelva a intentar.", null);
            }
            recetaDAO.desactivarActivas(connection, idProducto);
            long idReceta = recetaDAO.insertarReceta(connection, idProducto, version,
                    solicitud.rendimiento());
            recetaDAO.insertarIngredientes(connection, idReceta, solicitud.ingredientes());
            return recetaDAO.buscarDetalle(connection, idReceta)
                    .orElseThrow(() -> new SQLException("La receta recién creada no se encontró"));
        });
    }

    private void validarInsumos(Connection connection, List<NuevoIngredienteDTO> ingredientes)
            throws SQLException {
        Set<Long> ids = new LinkedHashSet<>();
        for (NuevoIngredienteDTO ingrediente : ingredientes) {
            ids.add(ingrediente.idInsumo());
        }
        Map<Long, ProductoReceta> insumos = recetaDAO.buscarProductos(connection, ids);
        for (Long id : ids) {
            ProductoReceta insumo = insumos.get(id);
            if (insumo == null) {
                throw new RecetaSolicitudInvalidaException(
                        "El ingrediente #" + id + " no existe", null);
            }
            if (!"INSUMO".equals(insumo.tipo()) || !"ACTIVO".equals(insumo.estado())) {
                throw new RecetaConflictoException(
                        "El ingrediente \"" + insumo.nombre() + "\" no es un insumo activo", null);
            }
        }
    }

    /** Reglas que no necesitan la base; se revisan antes de abrir la conexion. */
    static void validar(NuevaRecetaDTO solicitud) {
        if (solicitud == null) {
            throw invalida("Los datos de la receta son obligatorios");
        }
        if (solicitud.idProducto() == null || solicitud.idProducto() <= 0) {
            throw invalida("El ID del producto debe ser positivo");
        }
        validarNumero(solicitud.rendimiento(), "El rendimiento", "obligatorio");
        List<NuevoIngredienteDTO> ingredientes = solicitud.ingredientes();
        if (ingredientes == null || ingredientes.isEmpty()) {
            throw invalida("La receta debe tener al menos un ingrediente");
        }
        if (ingredientes.size() > MAXIMO_INGREDIENTES) {
            throw invalida("La receta no puede tener más de " + MAXIMO_INGREDIENTES
                    + " ingredientes");
        }
        Set<Long> vistos = new LinkedHashSet<>();
        for (NuevoIngredienteDTO ingrediente : ingredientes) {
            if (ingrediente == null || ingrediente.idInsumo() == null
                    || ingrediente.idInsumo() <= 0) {
                throw invalida("El ID de cada ingrediente debe ser positivo");
            }
            validarNumero(ingrediente.cantidad(), "La cantidad de cada ingrediente",
                    "obligatoria");
            if (!vistos.add(ingrediente.idInsumo())) {
                throw invalida("El ingrediente #" + ingrediente.idInsumo() + " está repetido");
            }
        }
    }

    /** NUMBER(12,3): positivo, hasta 3 decimales y 9 enteros. */
    private static void validarNumero(BigDecimal valor, String campo, String obligatorio) {
        if (valor == null) {
            throw invalida(campo + " es " + obligatorio);
        }
        if (valor.signum() <= 0) {
            throw invalida(campo + " debe ser mayor que cero");
        }
        if (valor.stripTrailingZeros().scale() > DECIMALES) {
            throw invalida(campo + " admite como máximo 3 decimales");
        }
        if (valor.compareTo(MAXIMO_NUMERO) > 0) {
            throw invalida(campo + " no puede superar 999999999.999");
        }
    }

    private <T> T transaccion(Trabajo<T> trabajo) {
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException errorRollback) {
                    exception.addSuppressed(errorRollback);
                }
                if (exception instanceof SQLException sql) throw traducirErrorOracle(sql);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible guardar la receta", exception);
        }
    }

    @FunctionalInterface
    private interface Trabajo<T> {
        T ejecutar(Connection connection) throws SQLException;
    }

    /** ORA-00001 solo puede venir de UNIQUE(id_producto, version): otro guardado a la vez. */
    static RuntimeException traducirErrorOracle(SQLException exception) {
        return switch (Math.abs(exception.getErrorCode())) {
            case 1 -> new RecetaConflictoException("Otro usuario guardó una versión de esta "
                    + "receta al mismo tiempo; intente de nuevo", exception);
            default -> new DataAccessException("No fue posible guardar la receta", exception);
        };
    }

    private static RecetaNoEncontradaException productoNoEncontrado(long idProducto) {
        return new RecetaNoEncontradaException("No existe un producto con ID " + idProducto, null);
    }

    private static RecetaSolicitudInvalidaException invalida(String mensaje) {
        return new RecetaSolicitudInvalidaException(mensaje, null);
    }

    private static void validarId(long id, String mensaje) {
        if (id <= 0) {
            throw invalida(mensaje);
        }
    }
}
