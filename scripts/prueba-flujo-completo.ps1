<#
.SYNOPSIS
    Prueba de extremo a extremo del flujo Pedido -> Produccion -> Inventario -> Despacho
    contra la API REST desplegada en Tomcat.

.DESCRIPTION
    ATENCION: este script ESCRIBE en Oracle. Crea un pedido, una produccion y un despacho,
    mueve inventario (consumo de insumos, producto terminado, despacho) y registra un ajuste
    de entrada +1 y salida -1 del producto. Los registros quedan guardados de forma permanente.

    Antes de escribir hace una verificacion de solo lectura y pide confirmacion
    (escribir SI), salvo que se use -Confirmar.

    Ademas del camino feliz, provoca los errores de negocio esperados (409/404/400)
    para comprobar que Oracle los rechaza sin dejar cambios parciales.

    La API exige sesion: el script inicia sesion con -Usuario y pide la contrasena sin
    mostrarla (o la recibe como SecureString en -Clave). Las operaciones se registran a
    nombre de ese usuario: la API toma el usuario de la sesion e ignora cualquier idUsuario
    del JSON. Las escrituras exigen un usuario ADMINISTRADOR, el unico rol
    que puede pedir, producir, ajustar inventario y despachar: con otro rol el script
    solo permite -SoloVerificar y se niega a escribir. Al terminar cierra la sesion.

.EXAMPLE
    .\scripts\prueba-flujo-completo.ps1 -Usuario admin -SoloVerificar
.EXAMPLE
    .\scripts\prueba-flujo-completo.ps1 -Usuario admin
.EXAMPLE
    .\scripts\prueba-flujo-completo.ps1 -Usuario admin -BaseUrl http://localhost:8080/panaderia/api -Cantidad 5
#>
param(
    [Parameter(Mandatory = $true)]
    [string]  $Usuario,             # usuario con el que se inicia sesion (ADMINISTRADOR)
    [System.Security.SecureString] $Clave,  # si se omite, se pide sin mostrarla
    [string]  $BaseUrl    = "http://localhost:8080/api",
    [long]    $IdSucursal = 1,      # CENTRAL
    [long]    $IdProducto = 33,     # ESPECIAL: producto final con receta activa
    [decimal] $Cantidad   = 10,
    [long[]]  $Insumos    = @(21, 23, 25, 27, 28),  # insumos de la receta activa del producto
    [switch]  $Confirmar,
    [switch]  $SoloVerificar  # ejecuta solo la verificacion de lectura y termina sin escribir
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
# El manejador guarda la cookie de sesion (JSESSIONID) y la envia en cada peticion.
$manejador = New-Object System.Net.Http.HttpClientHandler
$manejador.CookieContainer = New-Object System.Net.CookieContainer
$manejador.UseCookies = $true
$cliente = New-Object System.Net.Http.HttpClient($manejador)
$script:ok = 0
$script:fallas = 0
$marca = "PRUEBA FLUJO " + (Get-Date -Format "yyyy-MM-dd HH:mm:ss")

function Invoke-Api([string] $Metodo, [string] $Ruta, $Cuerpo = $null) {
    $solicitud = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), "$BaseUrl$Ruta")
    if ($null -ne $Cuerpo) {
        $json = $Cuerpo | ConvertTo-Json -Depth 5 -Compress
        $solicitud.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
    }
    $respuesta = $cliente.SendAsync($solicitud).Result
    $texto = $respuesta.Content.ReadAsStringAsync().Result
    $datos = $null
    if ($texto) { try { $datos = $texto | ConvertFrom-Json } catch { $datos = $null } }
    [pscustomobject]@{
        Status   = [int] $respuesta.StatusCode
        Datos    = $datos
        Texto    = $texto
        Location = $respuesta.Headers.Location
    }
}

function Comprobar([string] $Nombre, [bool] $Condicion, [string] $Detalle = "") {
    if ($Condicion) {
        $script:ok++
        Write-Host ("  OK     " + $Nombre) -ForegroundColor Green
    } else {
        $script:fallas++
        Write-Host ("  FALLA  " + $Nombre + "  " + $Detalle) -ForegroundColor Red
    }
}

function Esperar-Status($Respuesta, [int] $Esperado, [string] $Nombre) {
    $detalle = "esperado $Esperado, obtenido $($Respuesta.Status): $($Respuesta.Texto)"
    Comprobar "$Nombre -> $Esperado" ($Respuesta.Status -eq $Esperado) $detalle
}

function Requerir-Status($Respuesta, [int] $Esperado, [string] $Nombre) {
    Esperar-Status $Respuesta $Esperado $Nombre
    if ($Respuesta.Status -ne $Esperado) { throw "Paso critico fallido: $Nombre. Se detiene la prueba." }
}

function Obtener-Existencia([long] $Id) {
    $r = Invoke-Api GET "/inventario/productos/$Id/existencia"
    if ($r.Status -ne 200) { throw "No se pudo leer la existencia del producto $Id (HTTP $($r.Status))" }
    [decimal] $r.Datos.existencia
}

function Obtener-EstadoPedido([long] $Id) {
    (Invoke-Api GET "/pedidos/$Id").Datos.estado
}

function Titulo([string] $Texto) { Write-Host ""; Write-Host "== $Texto ==" -ForegroundColor Cyan }

$script:sesionAbierta = $false
function Cerrar-Sesion {
    if ($script:sesionAbierta) {
        try { [void] (Invoke-Api POST "/auth/logout") } catch { }
        $script:sesionAbierta = $false
    }
}

# Un error fuera del bloque try principal (p. ej. en la verificacion previa)
# tambien cierra la sesion antes de terminar.
trap { Cerrar-Sesion; $cliente.Dispose(); break }

# ---------------------------------------------------------------- Inicio de sesion
Titulo "Inicio de sesion"
if ($null -eq $Clave) {
    $Clave = Read-Host "  Contrasena de $Usuario" -AsSecureString
}
$puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Clave)
try {
    $login = Invoke-Api POST "/auth/login" @{
        username = $Usuario
        password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero)
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero)
}
if ($login.Status -ne 200) {
    $motivo = if ($login.Datos -and $login.Datos.message) { $login.Datos.message } else { "HTTP $($login.Status)" }
    Write-Host "  No se pudo iniciar sesion: $motivo No se escribio nada." -ForegroundColor Red
    $cliente.Dispose()
    exit 1
}
$script:sesionAbierta = $true
$usuarioSesion = $login.Datos
Comprobar "login como $($usuarioSesion.username) (rol $($usuarioSesion.rol))" $true
Requerir-Status (Invoke-Api GET "/auth/me") 200 "GET /auth/me con la cookie de sesion"
$esAdministrador = $usuarioSesion.rol -eq "ADMINISTRADOR"
if (-not $esAdministrador) {
    Write-Host "  Aviso: con el rol $($usuarioSesion.rol) solo se permite -SoloVerificar; las escrituras exigen ADMINISTRADOR." -ForegroundColor Yellow
}

# ---------------------------------------------------------------- 0. Verificacion (solo lectura)
Titulo "0. Verificacion previa (solo lectura)"
Requerir-Status (Invoke-Api GET "/health") 200 "GET /health"
$producto = Invoke-Api GET "/productos/$IdProducto"
Requerir-Status $producto 200 "GET /productos/$IdProducto"
Comprobar "producto $IdProducto es PRODUCTO_FINAL y ACTIVO" `
    ($producto.Datos.nombreTipoProducto -eq "PRODUCTO_FINAL" -and $producto.Datos.estado -eq "ACTIVO")
Requerir-Status (Invoke-Api GET "/recetas/productos/$IdProducto/activa") 200 "receta activa del producto $IdProducto"

$existenciaInicial = Obtener-Existencia $IdProducto
$insumosInicial = @{}
foreach ($i in $Insumos) { $insumosInicial[$i] = Obtener-Existencia $i }

Write-Host ""
Write-Host "  API:        $BaseUrl"
Write-Host "  Usuario:    $($usuarioSesion.username) (id $($usuarioSesion.idUsuario), rol $($usuarioSesion.rol))   Sucursal: $IdSucursal"
Write-Host "  Producto:   $IdProducto ($($producto.Datos.nombre))   Cantidad: $Cantidad   Existencia: $existenciaInicial"
Write-Host ("  Insumos:    " + (($Insumos | ForEach-Object { "$_=$($insumosInicial[$_])" }) -join ", "))
Write-Host "  Marca:      $marca"
Write-Host ""
Write-Host "  Se crearan un pedido, una produccion y un despacho, y se movera inventario." -ForegroundColor Yellow
Write-Host "  Estos cambios en Oracle son PERMANENTES." -ForegroundColor Yellow

if ($SoloVerificar) {
    Write-Host ""
    Write-Host "Solo verificacion: comprobaciones OK $script:ok, fallas $script:fallas. No se escribio nada."
    Cerrar-Sesion
    $cliente.Dispose()
    if ($script:fallas -gt 0) { exit 1 } else { exit 0 }
}

# Las escrituras son permanentes: solo un ADMINISTRADOR puede ejecutarlas desde
# este script, aunque la API todavia no aplique la matriz de roles.
if (-not $esAdministrador) {
    Write-Host ""
    Write-Host "  El flujo completo exige un usuario ADMINISTRADOR; $($usuarioSesion.username) tiene rol $($usuarioSesion.rol). No se escribio nada." -ForegroundColor Red
    Cerrar-Sesion
    $cliente.Dispose()
    exit 1
}

if (-not $Confirmar) {
    $respuestaUsuario = Read-Host "  Escribe SI para continuar"
    if ($respuestaUsuario -ne "SI") { Write-Host "Cancelado. No se escribio nada."; Cerrar-Sesion; $cliente.Dispose(); exit 0 }
}

try {
    # ------------------------------------------------------------ 1. Pedido
    Titulo "1. Crear y confirmar pedido"
    $fechaEntrega = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
    $r = Invoke-Api POST "/pedidos" @{
        idSucursal   = $IdSucursal
        fechaEntrega = $fechaEntrega
        observacion  = $marca
        detalles     = @(@{ idProducto = $IdProducto; cantidad = $Cantidad })
    }
    Requerir-Status $r 201 "POST /pedidos"
    $idPedido = [long] $r.Datos.idPedido
    Write-Host "  idPedido = $idPedido"
    Comprobar "pedido queda CONFIRMADO" ((Obtener-EstadoPedido $idPedido) -eq "CONFIRMADO")

    # ------------------------------------------------------------ 2. Produccion
    Titulo "2. Crear produccion y asociar pedido"
    $r = Invoke-Api POST "/producciones" @{ observacion = $marca }
    Requerir-Status $r 201 "POST /producciones"
    $idProduccion = [long] $r.Datos.idProduccion
    Write-Host "  idProduccion = $idProduccion"
    $r = Invoke-Api GET "/producciones/$idProduccion/detalle"
    Esperar-Status $r 200 "detalle antes del plan"
    Comprobar "detalle vacio antes del plan" (@($r.Datos).Count -eq 0)

    Requerir-Status (Invoke-Api POST "/producciones/$idProduccion/pedidos" @{ idPedido = $idPedido }) 200 "asociar pedido"
    Esperar-Status (Invoke-Api POST "/producciones/$idProduccion/pedidos" @{ idPedido = $idPedido }) 409 "asociar el mismo pedido otra vez (20404)"

    # ------------------------------------------------------------ 3. Plan
    Titulo "3. Generar plan"
    $r = Invoke-Api POST "/producciones/$idProduccion/generar-plan"
    Requerir-Status $r 200 "generar plan"
    $linea = @($r.Datos) | Where-Object { $_.idProducto -eq $IdProducto } | Select-Object -First 1
    Comprobar "plan incluye producto $IdProducto con cantidad $Cantidad" `
        ($null -ne $linea -and [decimal] $linea.cantidadPlanificada -eq $Cantidad) "$($r.Texto)"
    Comprobar "pedido pasa a EN_PRODUCCION" ((Obtener-EstadoPedido $idPedido) -eq "EN_PRODUCCION")

    Esperar-Status (Invoke-Api POST "/producciones/$idProduccion/generar-plan") 409 "generar plan otra vez (20405)"
    Esperar-Status (Invoke-Api POST "/pedidos/$idPedido/despacho") 409 "despachar antes de producir (20501)"
    Esperar-Status (Invoke-Api PUT "/producciones/$idProduccion/productos/999999/cantidad-producida" @{ cantidadProducida = 1 }) 404 "producir un producto ajeno al plan (20411)"
    Esperar-Status (Invoke-Api PUT "/producciones/$idProduccion/productos/$IdProducto/cantidad-producida" @{ cantidadProducida = 0 }) 400 "cantidad producida 0"

    # ------------------------------------------------------------ 4. Producir y finalizar
    Titulo "4. Registrar lo producido y finalizar"
    $rutaProducido = "/producciones/$idProduccion/productos/$IdProducto/cantidad-producida"
    if ($Cantidad -gt 1) {
        Requerir-Status (Invoke-Api PUT $rutaProducido @{ cantidadProducida = ($Cantidad - 1) }) 200 "registrar cantidad menor a la planificada"
        Esperar-Status (Invoke-Api POST "/producciones/$idProduccion/finalizar") 409 "finalizar con produccion incompleta (20413)"
        Comprobar "el intento fallido no movio inventario" ((Obtener-Existencia $IdProducto) -eq $existenciaInicial)
    }
    Requerir-Status (Invoke-Api PUT $rutaProducido @{ cantidadProducida = $Cantidad }) 200 "registrar cantidad completa (reemplaza el valor anterior)"
    $r = Invoke-Api GET "/producciones/$idProduccion/detalle"
    $linea = @($r.Datos) | Where-Object { $_.idProducto -eq $IdProducto } | Select-Object -First 1
    Comprobar "cantidad producida = $Cantidad (no acumula)" ([decimal] $linea.cantidadProducida -eq $Cantidad)

    Requerir-Status (Invoke-Api POST "/producciones/$idProduccion/finalizar") 200 "finalizar produccion"
    $existenciaProducida = Obtener-Existencia $IdProducto
    Comprobar "producto terminado +$Cantidad ($existenciaInicial -> $existenciaProducida)" ($existenciaProducida -eq $existenciaInicial + $Cantidad)
    foreach ($i in $Insumos) {
        $actual = Obtener-Existencia $i
        Comprobar "insumo $i consumido ($($insumosInicial[$i]) -> $actual)" ($actual -lt $insumosInicial[$i])
    }
    Comprobar "pedido pasa a LISTO_DESPACHO" ((Obtener-EstadoPedido $idPedido) -eq "LISTO_DESPACHO")
    Esperar-Status (Invoke-Api POST "/producciones/$idProduccion/finalizar") 409 "finalizar otra vez (20412)"

    # ------------------------------------------------------------ 5. Inventario manual
    Titulo "5. Ajustes manuales de inventario"
    $r = Invoke-Api POST "/inventario/productos/$IdProducto/entradas" @{ cantidad = 1; observacion = $marca }
    Requerir-Status $r 200 "entrada +1 (AJUSTE)"
    Comprobar "existencia devuelta = $($existenciaProducida + 1)" ([decimal] $r.Datos.existencia -eq $existenciaProducida + 1)
    $r = Invoke-Api POST "/inventario/productos/$IdProducto/salidas" @{ cantidad = 1; observacion = $marca }
    Requerir-Status $r 200 "salida -1 (AJUSTE)"
    Comprobar "existencia vuelve a $existenciaProducida" ([decimal] $r.Datos.existencia -eq $existenciaProducida)
    Esperar-Status (Invoke-Api POST "/inventario/productos/$IdProducto/salidas" @{ cantidad = 99999999 }) 409 "salida mayor a la existencia (20003)"
    $r = Invoke-Api POST "/inventario/productos/$IdProducto/entradas" @{ idUsuario = 999999; cantidad = 1; observacion = $marca }
    Requerir-Status $r 200 "idUsuario falso en el JSON se ignora: entrada a nombre de la sesion"
    Requerir-Status (Invoke-Api POST "/inventario/productos/$IdProducto/salidas" @{ cantidad = 1; observacion = $marca }) 200 "salida -1 que compensa esa entrada"
    Esperar-Status (Invoke-Api POST "/inventario/productos/999999999/entradas" @{ cantidad = 1 }) 404 "entrada a producto inexistente"
    Comprobar "existencia sin cambios tras los rechazos y la compensacion" ((Obtener-Existencia $IdProducto) -eq $existenciaProducida)

    # ------------------------------------------------------------ 6. Despacho
    Titulo "6. Despachar pedido"
    $r = Invoke-Api POST "/pedidos/$idPedido/despacho" @{ observacion = $marca }
    Requerir-Status $r 201 "despachar pedido"
    $idDespacho = [long] $r.Datos.idDespacho
    Write-Host "  idDespacho = $idDespacho"
    Comprobar "Location apunta a /pedidos/$idPedido/despacho" ("$($r.Location)" -like "*/pedidos/$idPedido/despacho")
    Comprobar "despacho en estado DESPACHADO" ($r.Datos.estado -eq "DESPACHADO")
    Esperar-Status (Invoke-Api GET "/pedidos/$idPedido/despacho") 200 "consultar despacho"
    Comprobar "pedido pasa a DESPACHADO" ((Obtener-EstadoPedido $idPedido) -eq "DESPACHADO")
    $existenciaFinal = Obtener-Existencia $IdProducto
    Comprobar "producto vuelve a la existencia inicial ($existenciaFinal = $existenciaInicial)" ($existenciaFinal -eq $existenciaInicial)
    Esperar-Status (Invoke-Api POST "/pedidos/$idPedido/despacho") 409 "despachar otra vez (20501)"
}
catch {
    $script:fallas++
    Write-Host ""
    Write-Host "  ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Titulo "Resumen"
    Write-Host "  Comprobaciones OK: $script:ok   Fallas: $script:fallas"
    Write-Host "  Registros creados (observacion '$marca'):"
    Write-Host "    pedido=$idPedido  produccion=$idProduccion  despacho=$idDespacho"
    Cerrar-Sesion
    $cliente.Dispose()
}

if ($script:fallas -gt 0) { exit 1 }
