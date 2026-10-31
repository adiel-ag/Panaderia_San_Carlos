package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ReporteDAO;
import gt.edu.umg.sancarlos.dto.AlertasDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/** Alertas de Inicio; cada conteo solo se calcula para los roles que ven esa pantalla. */
public class AlertaService {

    static final Set<String> VEN_PEDIDOS = Set.of("ADMINISTRADOR", "GERENTE", "SUCURSAL",
            "PRODUCCION", "DESPACHO");
    static final Set<String> VEN_RECETAS = Set.of("ADMINISTRADOR", "GERENTE", "PRODUCCION");
    static final Set<String> VEN_INVENTARIO = Set.of("ADMINISTRADOR", "GERENTE", "PRODUCCION",
            "BODEGA", "DESPACHO");

    private final ReporteDAO reporteDAO;

    public AlertaService() {
        this(new ReporteDAO());
    }

    AlertaService(ReporteDAO reporteDAO) {
        this.reporteDAO = reporteDAO;
    }

    public AlertasDTO alertas(UsuarioSesion usuario) {
        String rol = usuario.getRol();
        boolean sucursal = "SUCURSAL".equals(rol);
        // Un usuario SUCURSAL sin sucursal asignada no ve pedidos.
        boolean pedidos = VEN_PEDIDOS.contains(rol) && (!sucursal || usuario.getIdSucursal() != null);
        boolean recetas = VEN_RECETAS.contains(rol);
        boolean inventario = VEN_INVENTARIO.contains(rol);
        if (!pedidos && !recetas && !inventario) {
            return new AlertasDTO(0, 0, 0);
        }
        try (Connection connection = abrirConexion()) {
            long atrasados = pedidos ? reporteDAO.contarPedidosAtrasados(connection,
                    sucursal ? usuario.getIdSucursal() : null) : 0;
            long sinReceta = recetas ? reporteDAO.contarProductosSinReceta(connection) : 0;
            long bajoMinimo = inventario ? reporteDAO.contarInsumosBajoMinimo(connection) : 0;
            return new AlertasDTO(atrasados, sinReceta, bajoMinimo);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar las alertas", exception);
        }
    }

    /** Punto de extensión para pruebas sin Oracle. */
    Connection abrirConexion() throws SQLException {
        return ConnectionFactory.getConnection();
    }
}
