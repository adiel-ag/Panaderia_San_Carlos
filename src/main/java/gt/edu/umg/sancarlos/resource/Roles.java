package gt.edu.umg.sancarlos.resource;

/**
 * Nombres de los roles tal como estan en la tabla ROL de Oracle. Se usan en
 * @RolesAllowed como constantes para que un error de escritura falle al
 * compilar y no al ejecutar.
 */
final class Roles {

    static final String ADMINISTRADOR = "ADMINISTRADOR";
    static final String GERENTE = "GERENTE";
    static final String SUCURSAL = "SUCURSAL";
    static final String PRODUCCION = "PRODUCCION";
    static final String BODEGA = "BODEGA";
    static final String DESPACHO = "DESPACHO";

    private Roles() {
    }
}
