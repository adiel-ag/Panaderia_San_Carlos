package gt.edu.umg.sancarlos.dto;

public record SucursalAdminDTO(long idSucursal, String nombre, String direccion,
        String telefono, String estado, long usuariosActivos, long pedidosAbiertos) { }
