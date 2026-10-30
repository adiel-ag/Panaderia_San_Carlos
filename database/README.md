# Base de datos

Scripts para crear desde cero la base de datos Oracle de Panadería San Carlos:
16 tablas, los catálogos iniciales y los 5 paquetes de reglas de negocio.

Esta carpeta es la versión de referencia de los scripts. Se verificó contra la
base de datos real el 2026-10-29: columnas, tipos, valores por defecto, las 69
restricciones y el código de los paquetes coinciden.

## Contenido

| Ruta | Qué crea |
| --- | --- |
| `01_tablas/` | Las 16 tablas, numeradas en el orden en que deben crearse (cada una después de las tablas a las que apunta). |
| `02_datos_iniciales.sql` | Roles, sucursales, unidades de medida y tipos de producto. |
| `03_paquetes/` | `PKG_RECETA`, `PKG_INVENTARIO`, `PKG_PEDIDO`, `PKG_PRODUCCION` y `PKG_DESPACHO`, cada uno con su especificación y su cuerpo. |
| `instalar.sql` | Ejecuta todo lo anterior en orden y lista los objetos inválidos al final. |

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

Al final, `instalar.sql` verifica que existan y estén válidas las 16 tablas y
los 5 paquetes (especificación y cuerpo), y que estén cargados los tipos de
producto. La instalación terminó bien **solo** si aparece
`Instalacion terminada: 16 tablas y 5 paquetes validos` y SQL*Plus se cierra
solo con código 0. En los dos casos SQL*Plus termina por sí mismo; no queda
esperando en su prompt.

Si algo falla, el script se detiene con `ORA-20999: Instalacion incompleta` y
la lista de lo que falta, y sale con un código distinto de 0. Esto cubre los
casos que SQL*Plus no detiene por sí solo: un paquete que compila con errores
(queda creado pero inválido; `SHOW ERRORS` muestra la línea del error) y un
archivo que no se encuentra (SQL*Plus solo avisa `SP2-0310` y continúa).

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
- Contraseñas: la tabla `USUARIO` no las guarda.
