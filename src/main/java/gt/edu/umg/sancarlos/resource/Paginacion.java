package gt.edu.umg.sancarlos.resource;

import jakarta.ws.rs.BadRequestException;

/** Parametros de paginacion compartidos por los listados REST. */
public record Paginacion(int pagina, int tamano) {

    public static Paginacion de(String pagina, String tamano) {
        int numeroPagina = entero(pagina, 1, "La página debe ser un entero positivo");
        int numeroTamano = entero(tamano, 20, "El tamaño debe ser un entero entre 1 y 100");
        if (numeroTamano > 100) {
            throw new IllegalArgumentException("La página o el tamaño no son válidos");
        }
        return new Paginacion(numeroPagina, numeroTamano);
    }

    public static boolean valida(int pagina, int tamano) {
        return pagina >= 1 && tamano >= 1 && tamano <= 100;
    }

    private static int entero(String texto, int predeterminado, String mensaje) {
        if (texto == null) return predeterminado;
        try {
            int valor = Integer.parseInt(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se informa un error de solicitud sin detalles internos.
        }
        throw new BadRequestException(mensaje);
    }
}
