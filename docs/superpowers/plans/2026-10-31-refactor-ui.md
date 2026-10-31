# Refactorización UI — Plan de análisis y diseño

**Proyecto:** Panadería San Carlos  
**Fecha:** 31 de octubre de 2026  
**Estado:** implementación en curso. Login, pie institucional, salto al contenido y dashboard por rol están en código; los módulos operativos y la revisión visual completa siguen pendientes.

## 1. Objetivo

Hacer más clara y consistente la aplicación de trabajo diario sin alterar las reglas de pedidos, recetas, producción, inventario, despacho y usuarios. El resultado debe ser fácil de recorrer en computadora y tableta, y usable en teléfono cuando sea necesario. La refactorización comprende identidad visual, acceso, Inicio, navegación, formularios, tablas, HTML semántico y responsividad.

La dirección elegida por el usuario es **artesanal más expresiva**, con **café, crema y acentos ámbar**, títulos con carácter y texto de lectura sencilla. El acceso será una **tarjeta centrada y sobria**, conservará usuario y contraseña, e Inicio pasará de una lista de enlaces a un **panel con métricas y gráficos**. Los dispositivos principales serán computadora y tableta.

## 2. Estado actual verificado

| Área | Existe hoy | Oportunidad de mejora |
| --- | --- | --- |
| Identidad | Logo de la panadería, tonos cálidos, temas claro y oscuro, fuentes Fraunces y Nunito Sans. | Crear reglas de uso coherentes para jerarquía, superficies, acentos y estados. Dar más personalidad artesanal sin cargar las tablas de decoración. |
| Acceso | Formulario de usuario y contraseña en tarjeta centrada; mensaje de error y control de envío. | Afinar jerarquía, ayuda, recuperación ante error, foco y presentación institucional. Mantener el mecanismo de autenticación actual. |
| Inicio | Saludo y lista de módulos disponibles según el rol. | Mostrar tareas y números útiles del trabajo diario; enlazar cada cifra al módulo correspondiente. |
| Navegación | Encabezado, menú lateral y cajón móvil; rutas filtradas por rol. | Clarificar ubicación actual, reducir ruido del encabezado y asegurar acceso cómodo en tableta. |
| Datos | Tablas con desplazamiento horizontal y controles de 44 px en varios módulos. | Decidir por tabla cuándo conviene desplazamiento y cuándo una vista resumida; conservar acciones visibles y encabezados comprensibles. |
| Código visual | `tokens.css`, preset PrimeVue y un `style.css` extenso con reglas de varios módulos. | Consolidar componentes y estilos compartidos; dividir estilos por responsabilidad sin cambiar comportamiento. |
| Semántica | Ya se usan `header`, `nav`, `aside`, `main`, `section`, `form` y etiquetas de campo. | Revisar jerarquía de títulos, nombres accesibles, errores de campo, foco tras navegación y estados dinámicos. |

**Archivos de referencia:** `frontend/src/layouts/AppLayout.vue`, `frontend/src/views/LoginView.vue`, `frontend/src/views/InicioView.vue`, `frontend/src/router/menu.ts`, `frontend/src/tema/tokens.css`, `frontend/src/tema/preset.ts` y `frontend/src/style.css`. Los reportes previos de `.taste-skill/` sirven como línea base visual, no como aprobación del nuevo diseño.

## 3. Concepto visual

La identidad se inspira en el **obrador**: etiquetas claras, superficies cálidas y contraste de tinta sobre papel. La expresividad se reserva para marca, títulos, ilustraciones propias y estados importantes; formularios y tablas priorizan lectura rápida.

- **Color:** conservar la familia café/crema existente; usar ámbar para acciones principales y avisos que lo justifiquen. Definir tokens semánticos para fondo, superficie, texto, borde, foco, acción y estados. Validar cada par en tema claro y oscuro; no comunicar un estado solo por color.
- **Tipografía:** mantener como punto de partida Fraunces para títulos y Nunito Sans para lectura y controles. Ajustar escala, pesos y espaciado antes de considerar fuentes nuevas. Texto normal de al menos 16 px y cifras tabulares en datos operativos.
- **Composición:** un encabezado útil, navegación estable y contenido con anchuras apropiadas por tarea. Evitar tarjetas decorativas repetidas: cada bloque debe ofrecer una decisión, un dato o una acción.
- **Iconos e ilustración:** aprovechar PrimeIcons y las ilustraciones propias donde aporten orientación. Cada botón con solo icono tendrá nombre accesible. Animaciones discretas, con respeto a `prefers-reduced-motion`.
- **UMG:** identificación discreta en el pie del acceso y del marco de la aplicación, según la elección del usuario. Usar texto completo «Universidad Mariano Gálvez de Guatemala». No incorporar un logotipo sin un archivo autorizado.

## 4. Pantallas y comportamientos propuestos

### 4.1 Acceso

Conservar **usuario + contraseña + sesión del servidor**. No introducir registro público, proveedores externos ni cambios de seguridad para una refactorización visual. Usar tarjeta centrada, marca de la panadería, título claro, etiquetas permanentes, opción de mostrar contraseña, aviso cercano al formulario y botón de envío con estado de espera. Tras error, mantener usuario, vaciar contraseña y llevar el foco a un mensaje o campo útil. En móvil, la tarjeta ocupa el ancho disponible con margen seguro. El modo oscuro sigue disponible.

### 4.2 Inicio / dashboard

La primera zona responderá **«¿qué necesita atención ahora?»**. El usuario eligió priorizar la **operación del día por rol**; el histórico queda para una vista posterior si aporta una decisión concreta:

| Rol | Resumen útil | Acción directa |
| --- | --- | --- |
| Administrador / gerente | Pedidos pendientes, producción activa, pedidos listos, insumos bajo mínimo y productos sin receta. | Abrir módulo filtrado. |
| Sucursal | Sus pedidos pendientes y próximas entregas. | Registrar o consultar pedido. |
| Producción | Pedidos por planificar, producciones en proceso y productos sin receta. | Abrir producción o recetas. |
| Bodega | Insumos bajo mínimo o sin existencia registrada. | Abrir inventario. |
| Despacho | Pedidos listos para entregar. | Abrir despacho. |

Los **gráficos** se usarán solo cuando una comparación en el tiempo ayude a decidir: por ejemplo, pedidos por estado o entregas próximas por día. Cada gráfico tendrá título, período, leyenda, valores visibles o tabla equivalente y estados de carga, vacío y error. No mostrar porcentajes o tendencias si la API no proporciona el período completo. Para el primer corte se pueden calcular cifras a partir de listados existentes cuando el volumen sea pequeño y el total sea fiable; si la paginación o los permisos hacen incompleto el dato, diseñar un endpoint agregado específico antes de publicar la cifra. Nunca sumar únicamente la primera página como si fuera el total.

```text
┌ Encabezado: marca · sección actual · cuenta ──────────────┐
│ Menú │ Inicio: prioridad del día                           │
│      │ [Dato 1] [Dato 2] [Dato 3]                         │
│      │ Gráfico con período y alternativa textual           │
│      │ Tareas próximas · accesos a módulos                 │
└──────┴──────────────────────────────────────────────────────┘
```

### 4.3 Módulos operativos

Pedidos, Producción, Recetas, Inventario, Despacho y Usuarios conservarán sus tareas y permisos. Se aplicará una estructura común: título y explicación breve, acción principal, filtros, resultado, estado vacío/error y detalle. Las acciones peligrosas o irreversibles mostrarán consecuencias claras. Las vistas de detalle tendrán regreso predecible. Los formularios mostrarán errores junto al campo; si hay varios, un resumen al inicio enlazará a cada uno y recibirá foco tras un envío fallido.

## 5. HTML semántico y accesibilidad

La refactorización verificará: un `h1` por pantalla y niveles de título en orden; `main` único; `nav` con nombre; botones para acciones y enlaces para navegación; `label` unido a cada campo; tablas con encabezados claros; estados anunciados por lector de pantalla; foco visible y no tapado por cabeceras; recorrido completo con teclado; contraste mínimo 4.5:1 para texto normal y controles táctiles de 44 × 44 px cuando sea viable. Los mensajes de error estarán junto al campo y asociados con `aria-describedby`; el resumen de errores complementa esos avisos. El estado no dependerá solo de color o icono.

## 6. Responsividad

Diseñar desde el contenido y comprobar al menos **390, 768, 1024 y 1440 px**, con zoom de 200 % y temas claro/oscuro. En escritorio se mantiene menú lateral. En tableta se evaluará menú compacto o cajón según el espacio real de tablas y formularios. En teléfono se usa cajón, filtros apilados y acciones a ancho cómodo. Las tablas densas conservarán desplazamiento horizontal con indicación de que hay más columnas; tablas de pocas columnas podrán transformarse en filas resumidas si las etiquetas siguen claras. No debe aparecer desplazamiento horizontal de toda la página. Se atenderá `prefers-reduced-motion`.

## 7. Plan de ejecución por entregables

1. **Línea base:** capturas y recorrido de acceso, Inicio y cada módulo a los cuatro anchos; inventario de problemas concretos de contraste, foco, desbordes y consistencia. Registrar mediciones antes de editar.
2. **Sistema visual:** fijar tokens y reglas de tipografía, color, espacios, estados, botones, tablas y formularios. Conservar la identidad elegida y ambos temas. Validar contraste y componentes compartidos antes de aplicarlos por módulo.
3. **Marco y acceso:** refactorizar `AppLayout.vue`, `LoginView.vue` y estilos relacionados. Mantener rutas, sesión y permisos. Probar teclado, reintento de acceso y tamaños pequeños.
4. **Dashboard por rol:** definir primero las métricas con fuente y permiso; construir tarjetas informativas, gráfico accesible y acciones directas. Añadir endpoint agregado solo si las APIs paginadas no permiten calcular un total correcto. Probar estados de carga, datos vacíos, errores y rol distinto.
5. **Módulos:** aplicar componentes y estructura compartida a Pedidos, Producción, Recetas, Inventario, Despacho y Usuarios, módulo por módulo. Conservar pruebas funcionales y verificar cada flujo antes de pasar al siguiente.
6. **Cierre:** revisión visual real en navegador a 390/768/1024/1440 px, claro/oscuro, teclado y zoom; corregir hallazgos. Ejecutar `npm test`, `npm run build` y las pruebas Java si se añadió API. Comparar con la línea base y documentar límites.

## 8. Criterios de aceptación

- La pantalla de acceso conserva el inicio de sesión actual, informa errores comprensibles y funciona con teclado y en 390 px.
- Inicio muestra solo datos autorizados para el rol, con fuente y período claros; ninguna cifra se calcula desde una página incompleta.
- Cada gráfico tiene explicación y alternativa legible sin depender del color.
- Las pantallas operativas mantienen sus funciones y permisos; filtros, formularios y errores son consistentes.
- No hay desbordamiento de la página en los anchos acordados; las tablas anchas indican su desplazamiento.
- Los temas claro y oscuro mantienen contraste, foco visible y jerarquía de títulos.
- La identificación de UMG aparece en la ubicación finalmente elegida por el usuario.
- Las pruebas existentes siguen pasando y las capturas comparativas evidencian los cambios.

## 9. Decisiones confirmadas

1. **UMG:** pie discreto en acceso y aplicación.
2. **Dashboard:** prioridad a la operación del día según el rol; los gráficos deben apoyar esas decisiones y contar con datos completos y período explícito.

**Nota sobre la guía `ui-ux-pro-max`:** su búsqueda aportó reglas verificables para errores de formulario, foco y tablas responsivas. La dirección automática «funnel» corresponde a una página comercial y se descartó porque esta aplicación se utiliza para operaciones internas. La búsqueda específica de Vue no encontró una recomendación aplicable; se utilizaron las reglas generales de accesibilidad y la estructura ya existente del proyecto.

## 10. Avance de implementación

- [x] Pie institucional compartido en acceso y marco.
- [x] Dashboard por rol con totales completos de la API y barras con cantidades legibles.
- [x] Acceso con errores junto a campos vacíos, foco en el primer campo inválido y salto al contenido principal desde el marco.
- [x] Compilación Vue y suite frontend: 374 pruebas aprobadas el 31 de octubre de 2026.
- [x] Capturas del acceso en 1440, 768 y 390 px: `.taste-skill/ui-refactor-login/`. Se revisaron las tres vistas; no se observó desbordamiento. El inspector marcó la paleta cálida como `FAIL`, pero se mantiene porque el usuario la eligió expresamente; la tarjeta centrada explica el espacio libre a los lados.
- [ ] Revisar dashboard con datos reales en navegador y en temas claro/oscuro. El navegador integrado no accedió al servidor local; el inspector estático mostró el acceso sin sesión, no el dashboard.
- [ ] Refactorizar y revisar visualmente los módulos operativos, uno por uno, y completar la evaluación de teclado, zoom y contraste.
