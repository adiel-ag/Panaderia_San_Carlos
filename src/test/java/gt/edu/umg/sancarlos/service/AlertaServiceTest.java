package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.ReporteDAO;
import gt.edu.umg.sancarlos.dto.AlertasDTO;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qué conteo calcula cada rol, sin Oracle. */
class AlertaServiceTest {

    private final List<String> llamadas = new ArrayList<>();
    private int conexiones;

    private AlertaService servicio() {
        ReporteDAO dao = new ReporteDAO() {
            @Override public long contarPedidosAtrasados(Connection c, Long idSucursal) {
                llamadas.add("atrasados:" + idSucursal);
                return 4;
            }
            @Override public long contarProductosSinReceta(Connection c) {
                llamadas.add("sinReceta");
                return 2;
            }
            @Override public long contarInsumosBajoMinimo(Connection c) {
                llamadas.add("bajoMinimo");
                return 3;
            }
        };
        return new AlertaService(dao) {
            @Override Connection abrirConexion() {
                conexiones++;
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (p, m, a) -> null);
            }
        };
    }

    private static UsuarioSesion usuario(String rol, Long idSucursal) {
        return new UsuarioSesion(1, "u", "Usuario", rol, idSucursal);
    }

    @Test
    void administradorYGerenteVenLosTresConteos() {
        for (String rol : List.of("ADMINISTRADOR", "GERENTE")) {
            llamadas.clear();
            assertEquals(new AlertasDTO(4, 2, 3), servicio().alertas(usuario(rol, null)));
            assertEquals(List.of("atrasados:null", "sinReceta", "bajoMinimo"), llamadas);
        }
    }

    @Test
    void sucursalSoloCuentaSusPedidos() {
        assertEquals(new AlertasDTO(4, 0, 0), servicio().alertas(usuario("SUCURSAL", 7L)));
        assertEquals(List.of("atrasados:7"), llamadas);
    }

    @Test
    void sucursalSinSucursalAsignadaNoConsulta() {
        assertEquals(new AlertasDTO(0, 0, 0), servicio().alertas(usuario("SUCURSAL", null)));
        assertTrue(llamadas.isEmpty());
        assertEquals(0, conexiones);
    }

    @Test
    void produccionVePedidosRecetasEInventario() {
        assertEquals(new AlertasDTO(4, 2, 3), servicio().alertas(usuario("PRODUCCION", null)));
    }

    @Test
    void bodegaNoVePedidosNiRecetas() {
        assertEquals(new AlertasDTO(0, 0, 3), servicio().alertas(usuario("BODEGA", null)));
        assertEquals(List.of("bajoMinimo"), llamadas);
    }

    @Test
    void despachoVePedidosEInventarioPeroNoRecetas() {
        assertEquals(new AlertasDTO(4, 0, 3), servicio().alertas(usuario("DESPACHO", null)));
        assertEquals(List.of("atrasados:null", "bajoMinimo"), llamadas);
    }

    @Test
    void rangoDeReportesSeValida() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        ReporteService.validarRango(inicio, inicio);
        ReporteService.validarRango(inicio, inicio.plusDays(365));
        assertThrows(IllegalArgumentException.class,
                () -> ReporteService.validarRango(inicio, inicio.plusDays(366)));
        assertThrows(IllegalArgumentException.class,
                () -> ReporteService.validarRango(inicio, inicio.minusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ReporteService.validarRango(null, inicio));
        assertThrows(IllegalArgumentException.class,
                () -> ReporteService.validarRango(inicio, null));
    }
}
