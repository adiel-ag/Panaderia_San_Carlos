<#
.SYNOPSIS
    Comprueba sesiones, cookies, roles y alcance de sucursal de la API desplegada.

.DESCRIPTION
    Prueba de extremo a extremo que no crea ni modifica datos del negocio
    (pedidos, producciones, inventario, despachos ni usuarios): solo consulta, y
    las solicitudes a endpoints de escritura usan IdInexistente, de modo que se
    rechazan antes de escribir.

    ATENCION: no es de solo lectura en Oracle. Cada inicio de sesion correcto
    actualiza USUARIO_CREDENCIAL del usuario (ultimo_acceso, intentos_fallidos
    a 0 y bloqueado_hasta a NULL). Con -ProbarClaveIncorrecta ademas registra un
    intento fallido del usuario sucursal entre dos accesos correctos, de modo
    que el contador pasa de 0 a 1 y vuelve a 0 (el bloqueo es a los 5). El login
    de un usuario inexistente no escribe nada.

.EXAMPLE
    .\scripts\prueba-sesion-permisos.ps1
.EXAMPLE
    .\scripts\prueba-sesion-permisos.ps1 -PedidoPropio 2 -PedidoAjeno 1 -ProbarClaveIncorrecta
#>
param(
    [string] $Administrador = "admin",
    [string] $UsuarioSucursal = "chiva",
    [System.Security.SecureString] $ClaveAdministrador,
    [System.Security.SecureString] $ClaveSucursal,
    [string] $BaseUrl = "http://localhost:8080/api",
    [long] $PedidoPropio = 2,
    [long] $PedidoAjeno = 1,
    [long] $IdInexistente = 999999999,
    [long] $IdProducto = 33,
    [long] $IdProduccion = 4,
    [switch] $ProbarClaveIncorrecta
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$script:ok = 0
$script:fallas = 0
$script:sesionAdmin = $false
$script:sesionSucursal = $false

function Nuevo-Cliente {
    $manejador = New-Object System.Net.Http.HttpClientHandler
    $manejador.CookieContainer = New-Object System.Net.CookieContainer
    $manejador.UseCookies = $true
    return (New-Object System.Net.Http.HttpClient($manejador))
}

function Invoke-Api($Cliente, [string] $Metodo, [string] $Ruta, $Cuerpo = $null) {
    $solicitud = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), "$BaseUrl$Ruta")
    try {
        if ($null -ne $Cuerpo) {
            $json = $Cuerpo | ConvertTo-Json -Depth 5 -Compress
            $solicitud.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
        }
        $respuesta = $Cliente.SendAsync($solicitud).Result
        try {
            $texto = $respuesta.Content.ReadAsStringAsync().Result
            $datos = $null
            if ($texto) { try { $datos = $texto | ConvertFrom-Json } catch { $datos = $null } }
            $cookie = $null
            $valores = $null
            if ($respuesta.Headers.TryGetValues("Set-Cookie", [ref] $valores)) {
                $cookie = ($valores -join "; ")
            }
            return [pscustomobject]@{ Status = [int] $respuesta.StatusCode; Datos = $datos; Texto = $texto; SetCookie = $cookie }
        } finally { $respuesta.Dispose() }
    } finally { $solicitud.Dispose() }
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
    Comprobar "$Nombre -> $Esperado" ($Respuesta.Status -eq $Esperado) "esperado $Esperado, obtenido $($Respuesta.Status): $($Respuesta.Texto)"
}

function Requerir-Status($Respuesta, [int] $Esperado, [string] $Nombre) {
    Esperar-Status $Respuesta $Esperado $Nombre
    if ($Respuesta.Status -ne $Esperado) { throw "Paso critico fallido: $Nombre. Se detiene la prueba." }
}

function Titulo([string] $Texto) { Write-Host ""; Write-Host "== $Texto ==" -ForegroundColor Cyan }

function Iniciar-Sesion($Cliente, [string] $Usuario, [System.Security.SecureString] $Clave) {
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Clave)
    try {
        return Invoke-Api $Cliente POST "/auth/login" @{
            username = $Usuario
            password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero)
        }
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero)
    }
}

function Cerrar-Sesion($Cliente, [string] $Nombre) {
    $r = Invoke-Api $Cliente POST "/auth/logout"
    Esperar-Status $r 200 "logout $Nombre"
    Esperar-Status (Invoke-Api $Cliente GET "/auth/me") 401 "GET /auth/me despues de logout $Nombre"
    return $r.Status
}

$anonimo = Nuevo-Cliente
$admin = Nuevo-Cliente
$sucursal = Nuevo-Cliente
try {
    Titulo "0. Sin sesion"
    Esperar-Status (Invoke-Api $anonimo GET "/health") 200 "GET /health"
    Esperar-Status (Invoke-Api $anonimo GET "/auth/me") 401 "GET /auth/me"
    Esperar-Status (Invoke-Api $anonimo GET "/productos/$IdProducto") 401 "GET /productos/$IdProducto"
    Esperar-Status (Invoke-Api $anonimo GET "/pedidos/$PedidoPropio") 401 "GET /pedidos/$PedidoPropio"
    Esperar-Status (Invoke-Api $anonimo POST "/producciones/$IdInexistente/finalizar") 401 "finalizar sin sesion"
    Esperar-Status (Invoke-Api $anonimo POST "/inventario/productos/$IdInexistente/entradas" @{ cantidad = 1 }) 401 "entrada sin sesion"
    $usuarioFicticio = "usuario-que-no-existe-" + [guid]::NewGuid().ToString("N")
    Esperar-Status (Invoke-Api $anonimo POST "/auth/login" @{ username = $usuarioFicticio; password = "clave-ficticia" }) 401 "login de usuario inexistente"

    Titulo "1. Administrador"
    if ($null -eq $ClaveAdministrador) { $ClaveAdministrador = Read-Host "  Contrasena de $Administrador" -AsSecureString }
    $loginAdmin = Iniciar-Sesion $admin $Administrador $ClaveAdministrador
    Requerir-Status $loginAdmin 200 "login administrador"
    $script:sesionAdmin = $true
    $cookie = $loginAdmin.SetCookie
    Comprobar "Set-Cookie de login incluye JSESSIONID, HttpOnly y SameSite=Strict" `
        ($null -ne $cookie -and $cookie -match '(?i)JSESSIONID=' -and $cookie -match '(?i)HttpOnly' -and $cookie -match '(?i)SameSite\s*=\s*Strict') `
        "La cabecera Set-Cookie no es visible en HttpClient o falta un atributo: $cookie"
    $perfilAdmin = Invoke-Api $admin GET "/auth/me"
    Requerir-Status $perfilAdmin 200 "GET /auth/me administrador"
    Comprobar "rol ADMINISTRADOR" ($perfilAdmin.Datos.rol -eq "ADMINISTRADOR") "rol obtenido: $($perfilAdmin.Datos.rol)"
    if ($perfilAdmin.Datos.rol -ne "ADMINISTRADOR") { throw "Se detiene: el usuario $Administrador no es ADMINISTRADOR." }
    $ajenoAdmin = Invoke-Api $admin GET "/pedidos/$PedidoAjeno"
    Requerir-Status $ajenoAdmin 200 "pedido ajeno como administrador"
    $propioAdmin = Invoke-Api $admin GET "/pedidos/$PedidoPropio"
    Requerir-Status $propioAdmin 200 "pedido propio como administrador"
    $sucursalAjena = $ajenoAdmin.Datos.idSucursal
    $sucursalPropia = $propioAdmin.Datos.idSucursal
    Comprobar "pedidos de sucursales distintas" ($null -ne $sucursalAjena -and $null -ne $sucursalPropia -and "$sucursalAjena" -ne "$sucursalPropia") "ajeno=$sucursalAjena, propio=$sucursalPropia"
    if ($null -eq $sucursalAjena -or $null -eq $sucursalPropia -or "$sucursalAjena" -eq "$sucursalPropia") {
        throw "Se detiene: PedidoPropio y PedidoAjeno deben pertenecer a sucursales distintas."
    }
    $despachoAdmin = Invoke-Api $admin GET "/pedidos/$PedidoAjeno/despacho"
    if ($despachoAdmin.Status -eq 404) {
        Write-Host "  Nota: el pedido ajeno no tiene despacho; se omite la comparacion de sucursal." -ForegroundColor Yellow
    } else {
        Requerir-Status $despachoAdmin 200 "despacho del pedido ajeno como administrador"
        Comprobar "despacho pertenece a la sucursal del pedido" ("$($despachoAdmin.Datos.idSucursal)" -eq "$sucursalAjena")
    }
    Esperar-Status (Invoke-Api $admin GET "/producciones/$IdProduccion/detalle") 200 "detalle de produccion como administrador"
    Esperar-Status (Invoke-Api $admin GET "/inventario/productos/$IdProducto/existencia") 200 "existencia como administrador"

    Titulo "2. Usuario SUCURSAL"
    if ($null -eq $ClaveSucursal) { $ClaveSucursal = Read-Host "  Contrasena de $UsuarioSucursal" -AsSecureString }
    # El acceso correcto deja intentos_fallidos en 0 antes de cualquier intento fallido.
    $loginSucursal = Iniciar-Sesion $sucursal $UsuarioSucursal $ClaveSucursal
    Requerir-Status $loginSucursal 200 "login sucursal"
    $script:sesionSucursal = $true
    if ($ProbarClaveIncorrecta) {
        # Orden que nunca acerca la cuenta al bloqueo (5 fallos): el login anterior dejo el
        # contador en 0, el intento fallido lo sube a 1 y el acceso correcto inmediato lo
        # devuelve a 0. Se usan clientes aparte para no tocar la sesion de $sucursal.
        Write-Host "  Aviso: la clave incorrecta sube intentos_fallidos a 1; el acceso correcto siguiente lo restablece a 0." -ForegroundColor Yellow
        $claveFalsa = "incorrecta-" + [guid]::NewGuid().ToString("N")
        Esperar-Status (Invoke-Api $anonimo POST "/auth/login" @{ username = $UsuarioSucursal; password = $claveFalsa }) 401 "login con clave incorrecta"
        $restablecer = Nuevo-Cliente
        try {
            $reintento = Iniciar-Sesion $restablecer $UsuarioSucursal $ClaveSucursal
            Esperar-Status $reintento 200 "login correcto tras el fallo (contador vuelve a 0)"
            if ($reintento.Status -eq 200) { [void] (Invoke-Api $restablecer POST "/auth/logout") }
            else { Write-Host "  Revisa intentos_fallidos de ${UsuarioSucursal}: el contador quedo en 1." -ForegroundColor Yellow }
        } finally { $restablecer.Dispose() }
    }
    $perfilSucursal = Invoke-Api $sucursal GET "/auth/me"
    Requerir-Status $perfilSucursal 200 "GET /auth/me sucursal"
    Comprobar "rol SUCURSAL" ($perfilSucursal.Datos.rol -eq "SUCURSAL") "rol obtenido: $($perfilSucursal.Datos.rol)"
    if ($perfilSucursal.Datos.rol -ne "SUCURSAL") { throw "Se detiene: el usuario $UsuarioSucursal no es SUCURSAL." }
    Comprobar "sucursal del usuario coincide con PedidoPropio" ("$($perfilSucursal.Datos.idSucursal)" -eq "$sucursalPropia")
    if ("$($perfilSucursal.Datos.idSucursal)" -ne "$sucursalPropia") { throw "Se detiene: PedidoPropio debe pertenecer a la sucursal de $UsuarioSucursal." }
    Esperar-Status (Invoke-Api $sucursal GET "/pedidos/$PedidoPropio") 200 "pedido propio"
    $ajeno = Invoke-Api $sucursal GET "/pedidos/$PedidoAjeno"
    Esperar-Status $ajeno 404 "pedido ajeno"
    $inexistente = Invoke-Api $sucursal GET "/pedidos/$IdInexistente"
    Esperar-Status $inexistente 404 "pedido inexistente"
    # ApiError tiene status, error y message; solo message contiene el ID. Se normaliza
    # el numero completo en el JSON para comparar tambien los demas campos sin ocultarlos.
    $ajenoNormalizado = $ajeno.Texto -replace "(?<!\d)$PedidoAjeno(?!\d)", '<ID>'
    $inexistenteNormalizado = $inexistente.Texto -replace "(?<!\d)$IdInexistente(?!\d)", '<ID>'
    Comprobar "404 ajeno e inexistente tienen el mismo cuerpo" ($ajenoNormalizado -eq $inexistenteNormalizado) "ajeno=$($ajeno.Texto); inexistente=$($inexistente.Texto)"
    Esperar-Status (Invoke-Api $sucursal GET "/pedidos/$PedidoAjeno/despacho") 404 "despacho ajeno"
    Esperar-Status (Invoke-Api $sucursal GET "/productos/$IdProducto") 200 "producto como sucursal"
    Esperar-Status (Invoke-Api $sucursal GET "/producciones/$IdProduccion/detalle") 403 "detalle de produccion denegado"
    Esperar-Status (Invoke-Api $sucursal GET "/inventario/productos/$IdProducto/existencia") 403 "existencia denegada"
    Esperar-Status (Invoke-Api $sucursal POST "/producciones/$IdInexistente/finalizar") 403 "finalizar denegado"
    Esperar-Status (Invoke-Api $sucursal PUT "/producciones/$IdInexistente/productos/$IdInexistente/cantidad-producida" @{ cantidadProducida = 1 }) 403 "cantidad producida denegada"
    Esperar-Status (Invoke-Api $sucursal POST "/inventario/productos/$IdInexistente/entradas" @{ cantidad = 1 }) 403 "entrada denegada"
    Esperar-Status (Invoke-Api $sucursal POST "/inventario/productos/$IdInexistente/salidas" @{ cantidad = 1 }) 403 "salida denegada"
    Esperar-Status (Invoke-Api $sucursal POST "/pedidos/$IdInexistente/despacho") 403 "despacho denegado"
    Esperar-Status (Invoke-Api $sucursal PUT "/usuarios/$IdInexistente/clave" @{ password = "clave-ficticia" }) 403 "asignar clave denegado"

    Titulo "3. Cierre de sesion"
    $primerCierre = Cerrar-Sesion $sucursal "sucursal"
    if ($primerCierre -eq 200) { $script:sesionSucursal = $false }
    Esperar-Status (Invoke-Api $sucursal GET "/pedidos/$PedidoPropio") 401 "pedido despues de logout sucursal"
    Esperar-Status (Invoke-Api $sucursal POST "/auth/logout") $primerCierre "segundo logout sucursal"
    $primerCierre = Cerrar-Sesion $admin "administrador"
    if ($primerCierre -eq 200) { $script:sesionAdmin = $false }
    Esperar-Status (Invoke-Api $admin GET "/pedidos/$PedidoPropio") 401 "pedido despues de logout administrador"
    Esperar-Status (Invoke-Api $admin POST "/auth/logout") $primerCierre "segundo logout administrador"
} catch {
    $script:fallas++
    Write-Host "  FALLA  Paso interrumpido: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    if ($script:sesionSucursal) {
        try { [void] (Invoke-Api $sucursal POST "/auth/logout") } catch { Write-Host "  FALLA  No se pudo cerrar la sesion sucursal: $($_.Exception.Message)" -ForegroundColor Red; $script:fallas++ }
    }
    if ($script:sesionAdmin) {
        try { [void] (Invoke-Api $admin POST "/auth/logout") } catch { Write-Host "  FALLA  No se pudo cerrar la sesion administrador: $($_.Exception.Message)" -ForegroundColor Red; $script:fallas++ }
    }
    $anonimo.Dispose()
    $admin.Dispose()
    $sucursal.Dispose()
    Titulo "Resumen"
    Write-Host "  Comprobaciones OK: $script:ok   Fallas: $script:fallas"
}
if ($script:fallas -gt 0) { exit 1 }
