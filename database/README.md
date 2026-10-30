# Base de datos

Scripts para crear desde cero la base de datos Oracle de Panadería San Carlos:
17 tablas, los catálogos iniciales y los 6 paquetes de reglas de negocio.

Esta carpeta es la versión de referencia de los scripts. Se verificó contra la
base de datos real el 2026-10-29: columnas, tipos, valores por defecto, las 69
restricciones y el código de los paquetes coinciden. `USUARIO_CREDENCIAL` y
`PKG_SEGURIDAD` se agregaron después para el inicio de sesión (migración 001).

## Contenido

| Ruta | Qué crea |
| --- | --- |
| `01_tablas/` | Las 17 tablas, numeradas en el orden en que deben crearse (cada una después de las tablas a las que apunta). |
| `02_datos_iniciales.sql` | Roles, sucursales, unidades de medida y tipos de producto. |
| `03_paquetes/` | `PKG_RECETA`, `PKG_INVENTARIO`, `PKG_PEDIDO`, `PKG_PRODUCCION`, `PKG_DESPACHO` y `PKG_SEGURIDAD`, cada uno con su especificación y su cuerpo. |
| `instalar.sql` | Ejecuta todo lo anterior en orden y verifica que todo quedó válido. |
| `migraciones/` | Cambios para aplicar a una base ya instalada, con su script para revertirlos. |

## Cómo instalar

Solo en un esquema **vacío**: si las tablas ya existen, el script se detiene en
el primer error sin borrar nada.

```bat
cd database
set NLS_LANG=SPANISH_SPAIN.AL32UTF8
sqlplus usuario/contraseña@//host:puerto/servicio @instalar.sql
```

Si la base se identifica por SID (por ejemplo `XE`) en lugar de nombre de
servicio, usa el descriptor completo:

```bat
sqlplus usuario/contraseña@"(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SID=XE)))" @instalar.sql
```

`NLS_LANG` hace que las tildes de los mensajes de error y de los datos
iniciales se guarden correctamente. Sin esa variable, SQL*Plus en Windows
puede convertirlas en caracteres ilegibles.

Al final, `instalar.sql` verifica que existan y estén válidas las 17 tablas y
los 6 paquetes (especificación y cuerpo), y que estén cargados los tipos de
producto. La instalación terminó bien **solo** si aparece
`Instalacion terminada: 17 tablas y 6 paquetes validos` y SQL*Plus se cierra
solo con código 0. En los dos casos SQL*Plus termina por sí mismo; no queda
esperando en su prompt.

Si algo falla, el script se detiene con `ORA-20999: Instalacion incompleta` y
la lista de lo que falta, y sale con un código distinto de 0. Esto cubre los
casos que SQL*Plus no detiene por sí solo: un paquete que compila con errores
(queda creado pero inválido; `SHOW ERRORS` muestra la línea del error) y un
archivo que no se encuentra (SQL*Plus solo avisa `SP2-0310` y continúa).

## Migraciones

`instalar.sql` crea una base nueva con todo incluido. Una base **ya instalada**
se actualiza con los scripts de `migraciones/`, en orden y una sola vez cada
uno, desde la carpeta `database`:

| Migración | Qué agrega | Para revertir |
| --- | --- | --- |
| `001_seguridad.sql` | Tabla `USUARIO_CREDENCIAL` y paquete `PKG_SEGURIDAD` (inicio de sesión). No modifica nada existente. | `001_seguridad_revertir.sql` (borra todas las contraseñas guardadas) |

```bat
cd database
set NLS_LANG=SPANISH_SPAIN.AL32UTF8
sqlplus usuario/contraseña@... @migraciones/001_seguridad.sql
```

Igual que el instalador, termina con código 0 y el mensaje `Migracion 001
aplicada` solo si los objetos nuevos quedaron válidos; si la tabla ya existe,
se detiene antes de tocar el paquete.

## Inicio de sesión

La contraseña nunca se guarda: Java calcula su huella PBKDF2 con un salt
aleatorio por usuario y solo la huella se guarda en `USUARIO_CREDENCIAL`. La
huella guardada nunca sale de Oracle: `PKG_SEGURIDAD` la compara y decide.

| Procedimiento | Qué hace | Errores |
| --- | --- | --- |
| `SP_ESTABLECER_CLAVE` | Guarda o reemplaza la huella de un usuario y reinicia intentos y bloqueo. | -20601 usuario inexistente · -20604 datos de clave inválidos |
| `SP_OBTENER_PARAMETROS_CLAVE` | Devuelve algoritmo, iteraciones y salt para que Java calcule la huella de la clave escrita. | -20602 no disponible (usuario inexistente, usuario o rol inactivo, o sin clave) |
| `SP_VALIDAR_ACCESO` | Compara la huella y registra el resultado en una sola operación, con el usuario, su rol y su credencial reservados (`FOR UPDATE`). Devuelve 0 acceso correcto, 1 clave incorrecta, 2 incorrecta y cuenta bloqueada 15 minutos (al quinto intento). | -20602 no disponible · -20603 cuenta bloqueada · -20604 datos inválidos · -20605 la clave cambió durante el acceso (no cuenta como intento) |

Comparar y registrar en una sola operación evita aceptar una verificación
obsoleta: si entre los dos pasos, o mientras se valida, alguien cambia la
clave, desactiva al usuario o a su rol, o la cuenta se bloquea, la decisión
usa los datos vigentes: la validación espera a que esa otra operación termine
y vuelve a leer. El costo es que los accesos de usuarios con el mismo rol se
ordenan en fila durante los milisegundos que dura cada validación. Una clave
incorrecta se informa como resultado y no como error, para que quien llama
haga `COMMIT` y el intento fallido quede contado.

## Diferencias con los scripts originales

Los scripts originales están fuera del repositorio, en
`DOCUMENTACIÓN_PANADERIA_SAN_CARLOS/SCRIPTS_BD`. Al compararlos con la base
real se corrigieron tres puntos:

1. **`09_pedido.sql`:** la regla `ck_pedido_estado` no incluía `LISTO_DESPACHO`,
   estado que la base real admite y que usan `PKG_PRODUCCION` y `PKG_DESPACHO`.
2. **`05_usuario.sql`:** no existía script de la tabla `USUARIO`; se reconstruyó
   a partir de la definición de la base real.
3. **`02_datos_iniciales.sql`:** no se cargaban los tipos de producto `INSUMO` y
   `PRODUCTO_FINAL`, de los que depende `PKG_PEDIDO`.

El resto se copió sin cambios, salvo un encabezado que indica su origen.

## Qué no incluye

- Usuarios, productos, recetas, existencias de inventario ni operaciones: son
  datos del negocio y se registran aparte.
- Contraseñas iniciales: se asignan desde la aplicación, que calcula la huella
  (no se pueden cargar con SQL directo).
