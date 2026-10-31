package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.ExistenciaDTO;
import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.dto.NuevaRecetaDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.dto.RecetaResumenDTO;
import gt.edu.umg.sancarlos.dto.RecetaVersionDTO;
import gt.edu.umg.sancarlos.dto.ActualizarUsuarioDTO;
import gt.edu.umg.sancarlos.dto.CrearUsuarioDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import gt.edu.umg.sancarlos.dto.ProduccionConPedidosDTO;
import gt.edu.umg.sancarlos.dto.ExistenciaResumenDTO;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.service.InventarioService;
import gt.edu.umg.sancarlos.service.PedidoService;
import gt.edu.umg.sancarlos.service.ProduccionService;
import gt.edu.umg.sancarlos.service.ProductoService;
import gt.edu.umg.sancarlos.service.RecetaService;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import gt.edu.umg.sancarlos.service.AutenticacionService.SesionIniciada;
import gt.edu.umg.sancarlos.service.CatalogoService;
import gt.edu.umg.sancarlos.service.UsuarioService;
import java.time.LocalDateTime;
import java.time.LocalDate;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Priority;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La matriz de permisos aprobada, escrita como tabla. Si alguien cambia un
 * @RolesAllowed sin actualizar esta tabla (o al reves), la prueba falla.
 */
class MatrizRolesTest {

    private record CasoHttp(String metodo, String ruta, String json) {
    }

    private static final String ADMIN = "ADMINISTRADOR";
    private static final String GERENTE = "GERENTE";
    private static final String SUCURSAL = "SUCURSAL";
    private static final String PRODUCCION = "PRODUCCION";
    private static final String BODEGA = "BODEGA";
    private static final String DESPACHO = "DESPACHO";
    private static final Set<String> TODOS = Set.of(ADMIN, GERENTE, SUCURSAL, PRODUCCION, BODEGA, DESPACHO);

    private static final Map<String, Set<String>> MATRIZ = Map.ofEntries(
            Map.entry("ProductoResource.listarProductos", TODOS),
            Map.entry("ProductoResource.buscarProductoPorId", TODOS),
            Map.entry("RecetaResource.buscarRecetaActiva", TODOS),
            Map.entry("RecetaResource.listarRecetas", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("RecetaResource.buscarReceta", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("RecetaResource.listarVersiones", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("RecetaResource.crearReceta", Set.of(ADMIN, PRODUCCION)),
            Map.entry("PedidoResource.crearPedido", Set.of(ADMIN, SUCURSAL)),
            Map.entry("PedidoResource.buscarPedidoPorId", Set.of(ADMIN, GERENTE, SUCURSAL, PRODUCCION, DESPACHO)),
            Map.entry("PedidoResource.listarPedidos", Set.of(ADMIN, GERENTE, SUCURSAL, PRODUCCION, DESPACHO)),
            Map.entry("PedidoResource.cancelarPedido", Set.of(ADMIN, SUCURSAL)),
            Map.entry("ProduccionResource.crearProduccion", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.asociarPedido", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.generarPlan", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.registrarCantidadProducida", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.finalizarProduccion", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.cancelarProduccion", Set.of(ADMIN, PRODUCCION)),
            Map.entry("ProduccionResource.consultarDetalle", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("ProduccionResource.listarProducciones", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("ProduccionResource.buscarProduccionPorId", Set.of(ADMIN, GERENTE, PRODUCCION)),
            Map.entry("InventarioResource.consultarExistencia", Set.of(ADMIN, GERENTE, PRODUCCION, BODEGA, DESPACHO)),
            Map.entry("InventarioResource.listarExistencias", Set.of(ADMIN, GERENTE, PRODUCCION, BODEGA, DESPACHO)),
            Map.entry("InventarioResource.registrarEntrada", Set.of(ADMIN, BODEGA)),
            Map.entry("InventarioResource.registrarSalida", Set.of(ADMIN, BODEGA)),
            Map.entry("DespachoResource.despacharPedido", Set.of(ADMIN, DESPACHO)),
            Map.entry("DespachoResource.buscarDespacho", Set.of(ADMIN, GERENTE, SUCURSAL, DESPACHO)),
            Map.entry("RolResource.listarRoles", TODOS),
            Map.entry("SucursalResource.listarSucursales", TODOS),
            Map.entry("AuthResource.cambiarClave", TODOS),
            Map.entry("UsuarioResource.listarUsuarios", Set.of(ADMIN)),
            Map.entry("UsuarioResource.buscarUsuario", Set.of(ADMIN)),
            Map.entry("UsuarioResource.crearUsuario", Set.of(ADMIN)),
            Map.entry("UsuarioResource.actualizarUsuario", Set.of(ADMIN)),
            Map.entry("UsuarioResource.cambiarEstado", Set.of(ADMIN)),
            Map.entry("UsuarioResource.desbloquear", Set.of(ADMIN)),
            Map.entry("UsuarioResource.asignarClave", Set.of(ADMIN)));

    private static final Map<String, CasoHttp> CASOS_HTTP = Map.ofEntries(
            Map.entry("ProductoResource.listarProductos", new CasoHttp("GET", "productos", null)),
            Map.entry("ProductoResource.buscarProductoPorId", new CasoHttp("GET", "productos/33", null)),
            Map.entry("RecetaResource.buscarRecetaActiva", new CasoHttp("GET", "recetas/productos/33/activa", null)),
            Map.entry("RecetaResource.listarRecetas", new CasoHttp("GET", "recetas", null)),
            Map.entry("RecetaResource.buscarReceta", new CasoHttp("GET", "recetas/9", null)),
            Map.entry("RecetaResource.listarVersiones", new CasoHttp("GET", "recetas/productos/33/versiones", null)),
            Map.entry("RecetaResource.crearReceta", new CasoHttp("POST", "recetas",
                    "{\"idProducto\":33,\"rendimiento\":10,\"ingredientes\":[{\"idInsumo\":2,\"cantidad\":1.5}]}")),
            Map.entry("PedidoResource.crearPedido", new CasoHttp("POST", "pedidos", "{\"idSucursal\":1}")),
            Map.entry("PedidoResource.buscarPedidoPorId", new CasoHttp("GET", "pedidos/2", null)),
            Map.entry("PedidoResource.listarPedidos", new CasoHttp("GET", "pedidos", null)),
            Map.entry("PedidoResource.cancelarPedido", new CasoHttp("POST", "pedidos/2/cancelar", null)),
            Map.entry("ProduccionResource.crearProduccion", new CasoHttp("POST", "producciones", "{}")),
            Map.entry("ProduccionResource.asociarPedido", new CasoHttp("POST", "producciones/4/pedidos", "{\"idPedido\":2}")),
            Map.entry("ProduccionResource.generarPlan", new CasoHttp("POST", "producciones/4/generar-plan", null)),
            Map.entry("ProduccionResource.registrarCantidadProducida", new CasoHttp("PUT", "producciones/4/productos/33/cantidad-producida", "{\"cantidadProducida\":1}")),
            Map.entry("ProduccionResource.finalizarProduccion", new CasoHttp("POST", "producciones/4/finalizar", null)),
            Map.entry("ProduccionResource.cancelarProduccion", new CasoHttp("POST", "producciones/4/cancelar", null)),
            Map.entry("ProduccionResource.consultarDetalle", new CasoHttp("GET", "producciones/4/detalle", null)),
            Map.entry("ProduccionResource.listarProducciones", new CasoHttp("GET", "producciones", null)),
            Map.entry("ProduccionResource.buscarProduccionPorId", new CasoHttp("GET", "producciones/4", null)),
            Map.entry("InventarioResource.consultarExistencia", new CasoHttp("GET", "inventario/productos/33/existencia", null)),
            Map.entry("InventarioResource.listarExistencias", new CasoHttp("GET", "inventario/existencias", null)),
            Map.entry("InventarioResource.registrarEntrada", new CasoHttp("POST", "inventario/productos/33/entradas", "{\"cantidad\":1}")),
            Map.entry("InventarioResource.registrarSalida", new CasoHttp("POST", "inventario/productos/33/salidas", "{\"cantidad\":1}")),
            Map.entry("DespachoResource.despacharPedido", new CasoHttp("POST", "pedidos/2/despacho", null)),
            Map.entry("DespachoResource.buscarDespacho", new CasoHttp("GET", "pedidos/2/despacho", null)),
            Map.entry("RolResource.listarRoles", new CasoHttp("GET", "roles", null)),
            Map.entry("SucursalResource.listarSucursales", new CasoHttp("GET", "sucursales", null)),
            Map.entry("AuthResource.cambiarClave", new CasoHttp("PUT", "auth/clave", "{\"claveActual\":\"anterior-1\",\"claveNueva\":\"nueva-clave-1\"}")),
            Map.entry("UsuarioResource.listarUsuarios", new CasoHttp("GET", "usuarios", null)),
            Map.entry("UsuarioResource.buscarUsuario", new CasoHttp("GET", "usuarios/7", null)),
            Map.entry("UsuarioResource.crearUsuario", new CasoHttp("POST", "usuarios", "{\"nombre\":\"Ana\",\"username\":\"ana\",\"rol\":\"GERENTE\",\"password\":\"clave-nueva-1\"}")),
            Map.entry("UsuarioResource.actualizarUsuario", new CasoHttp("PUT", "usuarios/7", "{\"nombre\":\"Ana\",\"rol\":\"GERENTE\"}")),
            Map.entry("UsuarioResource.cambiarEstado", new CasoHttp("PUT", "usuarios/7/estado", "{\"estado\":\"ACTIVO\"}")),
            Map.entry("UsuarioResource.desbloquear", new CasoHttp("POST", "usuarios/7/desbloquear", null)),
            Map.entry("UsuarioResource.asignarClave", new CasoHttp("PUT", "usuarios/7/clave", "{\"password\":\"clave-de-prueba\"}")));

    @Test
    void cadaRolRecibe403OExitoEnCadaEndpointPorHttp() {
        assertEquals(MATRIZ.keySet(), CASOS_HTTP.keySet());
        for (Map.Entry<String, CasoHttp> entrada : CASOS_HTTP.entrySet()) {
            CasoHttp caso = entrada.getValue();
            for (String rol : TODOS) {
                int estado = llamar(rol, caso.metodo(), caso.ruta(), caso.json());
                String contexto = entrada.getKey() + " con " + rol;
                if (MATRIZ.get(entrada.getKey()).contains(rol)) {
                    assertTrue(estado >= 200 && estado < 300, contexto + ": HTTP " + estado);
                } else {
                    assertEquals(403, estado, contexto);
                }
            }
        }
    }

    @Test
    void sinSesionTodosLosEndpointsResponden401PorHttp() {
        assertEquals(MATRIZ.keySet(), CASOS_HTTP.keySet());
        for (Map.Entry<String, CasoHttp> entrada : CASOS_HTTP.entrySet()) {
            CasoHttp caso = entrada.getValue();
            assertEquals(401, llamar(null, caso.metodo(), caso.ruta(), caso.json()), entrada.getKey());
        }
    }

    @Test
    void cadaEndpointProtegidoDeclaraExactamenteLosRolesDeLaMatriz() throws Exception {
        Map<String, Set<String>> declarados = new TreeMap<>();
        for (Class<?> clase : clasesDeRecursos()) {
            for (Method metodo : clase.getDeclaredMethods()) {
                boolean endpoint = Arrays.stream(metodo.getAnnotations())
                        .anyMatch(a -> a.annotationType().isAnnotationPresent(HttpMethod.class));
                if (!endpoint || metodo.isAnnotationPresent(PermitAll.class)) {
                    continue;
                }
                RolesAllowed roles = metodo.getAnnotation(RolesAllowed.class);
                declarados.put(clase.getSimpleName() + "." + metodo.getName(),
                        roles == null ? Set.of() : Set.of(roles.value()));
            }
        }
        assertEquals(new TreeMap<>(MATRIZ), declarados);
    }

    @Test
    void lasConstantesDeRolesCoincidenConLaTablaRolDeOracle() throws Exception {
        Set<String> valores = new TreeSet<>();
        for (Field campo : Roles.class.getDeclaredFields()) {
            if (Modifier.isStatic(campo.getModifiers()) && campo.getType() == String.class) {
                campo.setAccessible(true);
                valores.add((String) campo.get(null));
            }
        }
        assertEquals(new TreeSet<>(TODOS), valores);
    }

    @Test
    void crearPedidoSoloAdministradorYSucursal() {
        for (String rol : TODOS) {
            int esperado = Set.of(ADMIN, SUCURSAL).contains(rol) ? 201 : 403;
            assertEquals(esperado, llamar(rol, "POST", "pedidos", "{}"), rol);
        }
    }

    @Test
    void despacharSoloAdministradorYDespacho() {
        for (String rol : TODOS) {
            int esperado = Set.of(ADMIN, DESPACHO).contains(rol) ? 201 : 403;
            assertEquals(esperado, llamar(rol, "POST", "pedidos/2/despacho", "{\"idUsuario\":1}"), rol);
        }
    }

    @Test
    void ajustesDeInventarioSoloAdministradorYBodega() {
        for (String rol : TODOS) {
            int esperado = Set.of(ADMIN, BODEGA).contains(rol) ? 200 : 403;
            assertEquals(esperado, llamar(rol, "POST", "inventario/productos/33/entradas",
                    "{\"idUsuario\":1,\"cantidad\":1}"), rol);
        }
    }

    @Test
    void detalleDeProduccionNoLoVenSucursalBodegaNiDespacho() {
        for (String rol : TODOS) {
            int esperado = Set.of(ADMIN, GERENTE, PRODUCCION).contains(rol) ? 200 : 403;
            assertEquals(esperado, llamar(rol, "GET", "producciones/4/detalle", null), rol);
        }
    }

    @Test
    void guardarRecetaSoloAdministradorYProduccion() {
        for (String rol : TODOS) {
            int esperado = Set.of(ADMIN, PRODUCCION).contains(rol) ? 201 : 403;
            assertEquals(esperado, llamar(rol, "POST", "recetas",
                    "{\"idProducto\":33,\"rendimiento\":1,\"ingredientes\":[]}"), rol);
        }
    }

    private static int llamar(String rol, String metodo, String ruta, String json) {
        PeticionFalsa peticion = new PeticionFalsa();
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new ProductoResource(new ProductoService() {
                    @Override
                    public List<Producto> listarProductos() {
                        return List.of();
                    }

                    @Override
                    public Optional<Producto> buscarProductoPorId(long idProducto) {
                        return Optional.of(new Producto());
                    }
                }))
                .register(new RecetaResource(new RecetaService() {
                    @Override
                    public RecetaActivaDTO buscarRecetaActiva(long idProducto) {
                        return new RecetaActivaDTO(idProducto, 9);
                    }

                    private RecetaDetalleDTO receta() {
                        return new RecetaDetalleDTO(9, 33, "Pan", "UND", 1, BigDecimal.TEN,
                                "ACTIVA", LocalDate.of(2026, 10, 31), null, true, List.of());
                    }
                    @Override public List<RecetaResumenDTO> listar() { return List.of(); }
                    @Override public RecetaDetalleDTO buscarPorId(long idReceta) { return receta(); }
                    @Override public List<RecetaVersionDTO> listarVersiones(long idProducto) {
                        return List.of();
                    }
                    @Override public RecetaDetalleDTO crear(NuevaRecetaDTO solicitud) { return receta(); }
                }))
                .register(new PedidoResource(new PedidoService() {
                    @Override
                    public long crearPedido(CrearPedidoDTO solicitud) {
                        return 501;
                    }

                    @Override
                    public Optional<Pedido> buscarPedidoPorId(long idPedido) {
                        Pedido pedido = new Pedido();
                        pedido.setIdPedido(idPedido);
                        pedido.setIdSucursal(1);
                        return Optional.of(pedido);
                    }

                    @Override public Pagina<PedidoResumenDTO> listar(int pagina, int tamano,
                            String estado, Long idSucursal, LocalDate desde, LocalDate hasta) {
                        return new Pagina<>(List.of(), 0, pagina, tamano);
                    }

                    @Override public Pedido cancelarPedido(long idPedido, UsuarioSesion usuario) {
                        Pedido pedido = new Pedido();
                        pedido.setIdPedido(idPedido);
                        pedido.setIdSucursal(1);
                        pedido.setEstado("CANCELADO");
                        return pedido;
                    }
                }))
                .register(new DespachoResource(new DespachoService() {
                    @Override
                    public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
                        return new Despacho();
                    }

                    @Override
                    public Optional<Despacho> buscarPorPedido(long idPedido) {
                        Despacho despacho = new Despacho();
                        despacho.setIdPedido(idPedido);
                        despacho.setIdSucursal(1L);
                        return Optional.of(despacho);
                    }
                }))
                .register(new InventarioResource(new InventarioService() {
                    @Override
                    public ExistenciaDTO consultarExistencia(long idProducto) {
                        return new ExistenciaDTO(idProducto, BigDecimal.ONE);
                    }

                    @Override public Pagina<ExistenciaResumenDTO> listarExistencias(int pagina,
                            int tamano, String tipo, String nombre, Boolean bajoMinimo) {
                        return new Pagina<>(List.of(), 0, pagina, tamano);
                    }

                    @Override
                    public ExistenciaDTO registrarEntrada(long idProducto, long idUsuario,
                            BigDecimal cantidad, String observacion) {
                        return new ExistenciaDTO(idProducto, BigDecimal.ONE);
                    }

                    @Override
                    public ExistenciaDTO registrarSalida(long idProducto, long idUsuario,
                            BigDecimal cantidad, String observacion) {
                        return new ExistenciaDTO(idProducto, BigDecimal.ZERO);
                    }
                }))
                .register(new ProduccionResource(new ProduccionService() {
                    @Override
                    public long crearProduccion(long idUsuario, String observacion) {
                        return 4;
                    }

                    @Override
                    public void asociarPedido(long idProduccion, long idPedido) {
                    }

                    @Override
                    public void generarPlan(long idProduccion) {
                    }

                    @Override
                    public void registrarProducido(long idProduccion, long idProducto, BigDecimal cantidad) {
                    }

                    @Override
                    public void finalizarProduccion(long idProduccion, long idUsuario) {
                    }

                    @Override
                    public List<ProduccionDetalle> consultarDetalle(long idProduccion) {
                        return List.of();
                    }

                    @Override public Pagina<ProduccionResumenDTO> listar(int pagina, int tamano,
                            String estado) {
                        return new Pagina<>(List.of(), 0, pagina, tamano);
                    }

                    @Override public ProduccionConPedidosDTO cancelarProduccion(long idProduccion) {
                        return buscarPorId(idProduccion);
                    }

                    @Override public ProduccionConPedidosDTO buscarPorId(long idProduccion) {
                        return new ProduccionConPedidosDTO(new ProduccionResumenDTO(
                                idProduccion, 1, "Usuario", LocalDateTime.of(2026, 10, 30, 9, 0),
                                "PLANIFICADA", null, 0), List.of());
                    }
                }))
                .register(new RolResource(new CatalogoService() {
                    @Override public List<gt.edu.umg.sancarlos.dto.RolDTO> roles() { return List.of(); }
                }))
                .register(new SucursalResource(new CatalogoService() {
                    @Override public List<gt.edu.umg.sancarlos.dto.SucursalDTO> sucursales() { return List.of(); }
                }))
                .register(new AuthResource(new AutenticacionService() {
                    @Override public SesionIniciada cambiarClave(UsuarioSesion usuario,
                            char[] actual, char[] nueva) {
                        return new SesionIniciada(usuario, LocalDateTime.of(2026, 10, 30, 9, 0));
                    }
                }))
                .register(new UsuarioResource(new AutenticacionService() {
                    @Override
                    public void establecerClave(long idUsuario, char[] clave) {
                    }
                }, new UsuarioService() {
                    private UsuarioDTO usuario() {
                        return new UsuarioDTO(7, "Ana", "ana", GERENTE, null, null,
                                "ACTIVO", null, null, false, true);
                    }
                    @Override public Pagina<UsuarioDTO> listar(int pagina, int tamano,
                            String filtroRol, String estado, Long idSucursal) {
                        return new Pagina<>(List.of(usuario()), 1, pagina, tamano);
                    }
                    @Override public UsuarioDTO buscarPorId(long id) { return usuario(); }
                    @Override public UsuarioDTO crear(CrearUsuarioDTO solicitud, char[] clave) { return usuario(); }
                    @Override public UsuarioDTO actualizar(long id, ActualizarUsuarioDTO solicitud) { return usuario(); }
                    @Override public UsuarioDTO cambiarEstado(long id, String estado) { return usuario(); }
                    @Override public UsuarioDTO desbloquear(long id) { return usuario(); }
                }))
                .register(new AbstractBinder() {
                    @Override protected void configure() {
                        bind(peticion.request).to(HttpServletRequest.class);
                    }
                })
                .register(new SesionConRol(rol))
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve(ruta), metodo, null,
                new MapPropertiesDelegate(), null);
        if (json != null) {
            request.getHeaders().putSingle("Content-Type", "application/json");
            request.setEntityStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            return handler.apply(request).get().getStatus();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** Hace de SesionFilter con un usuario del rol indicado. */
    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionConRol implements ContainerRequestFilter {

        private final String rol;

        SesionConRol(String rol) {
            this.rol = rol;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            if (rol != null) {
                requestContext.setSecurityContext(new UsuarioSecurityContext(
                        new UsuarioSesion(1, "u", "Usuario", rol, 1L), false));
            }
        }
    }

    private static List<Class<?>> clasesDeRecursos() throws Exception {
        java.nio.file.Path carpeta = java.nio.file.Path.of(
                HealthResource.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("gt/edu/umg/sancarlos/resource");
        try (Stream<java.nio.file.Path> archivos = Files.list(carpeta)) {
            List<Class<?>> clases = archivos
                    .map(a -> a.getFileName().toString())
                    .filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .map(n -> cargar("gt.edu.umg.sancarlos.resource." + n.substring(0, n.length() - 6)))
                    .filter(c -> c.isAnnotationPresent(Path.class))
                    .collect(Collectors.toList());
            assertTrue(clases.size() >= 11, "clases de recursos: " + clases);
            return clases;
        }
    }

    private static Class<?> cargar(String nombre) {
        try {
            return Class.forName(nombre);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }
}
