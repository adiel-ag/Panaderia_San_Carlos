<#
.SYNOPSIS
    Prueba de extremo a extremo de la gestion de usuarios (Fase 10.0a) contra la API.

.DESCRIPTION
    ATENCION: este script ESCRIBE en Oracle de forma permanente. Crea un usuario de
    prueba (-Username, BODEGA), le cambia el rol, lo bloquea con 5 intentos fallidos,
    lo desbloquea, le cambia la contrasena y al final lo deja INACTIVO. Los usuarios
    no se borran: queda en la base desactivado. Cada inicio de sesion correcto ademas
    actualiza USUARIO_CREDENCIAL.

    Comprueba: catalogos, validaciones al crear (400, no escriben), username repetido
    (409), permisos por rol, que un cambio de rol se aplica a la sesion abierta,
    bloqueo y desbloqueo, cambio de la propia contrasena (la sesion actual se renueva
    y las demas se invalidan), la regla del ultimo administrador (409, solo si hay
    uno activo) y que desactivar cierra las sesiones del usuario.

    Requiere las migraciones 002 y 003. La contrasena del administrador se pide sin
    mostrarla; las del usuario de prueba se generan al azar. Como el usuario queda
    creado, para repetir la prueba usa otro -Username.

.EXAMPLE
    .\scripts\prueba-gestion-usuarios.ps1
.EXAMPLE
    .\scripts\prueba-gestion-usuarios.ps1 -Username prueba.usuarios2
#>
param(
    [string] $BaseUrl = "http://localhost:8080/api",
    [string] $Administrador = "admin",
    [string] $Username = "prueba.usuarios"   # usuario de prueba que se crea
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$script:ok = 0; $script:fallas = 0

function Nuevo-Cliente {
    $m = New-Object System.Net.Http.HttpClientHandler
    $m.CookieContainer = New-Object System.Net.CookieContainer
    $m.UseCookies = $true
    New-Object System.Net.Http.HttpClient($m)
}
function Api($Cliente, [string] $Metodo, [string] $Ruta, $Cuerpo = $null) {
    $s = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), "$BaseUrl$Ruta")
    if ($null -ne $Cuerpo) {
        $s.Content = New-Object System.Net.Http.StringContent(($Cuerpo | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, "application/json")
    }
    $r = $Cliente.SendAsync($s).Result
    $t = $r.Content.ReadAsStringAsync().Result
    $d = $null; if ($t) { try { $d = $t | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Status = [int] $r.StatusCode; Datos = $d; Texto = $t }
}
function Ver([string] $Nombre, [bool] $Cond, [string] $Detalle = "") {
    if ($Cond) { $script:ok++; Write-Host "  OK     $Nombre" -ForegroundColor Green }
    else { $script:fallas++; Write-Host "  FALLA  $Nombre  $Detalle" -ForegroundColor Red }
}
function Status($R, [int] $Esperado, [string] $Nombre) {
    Ver "$Nombre -> $Esperado" ($R.Status -eq $Esperado) "obtenido $($R.Status): $($R.Texto)"
}
function Exigir($R, [int] $Esperado, [string] $Nombre) {
    Status $R $Esperado $Nombre
    if ($R.Status -ne $Esperado) { throw "Paso critico fallido: $Nombre" }
}
function Titulo([string] $T) { Write-Host ""; Write-Host "== $T ==" -ForegroundColor Cyan }
function Clave-Aleatoria { "Prueba-" + [guid]::NewGuid().ToString("N").Substring(0, 16) }

$admin = Nuevo-Cliente; $prueba = Nuevo-Cliente; $vieja = Nuevo-Cliente; $otro = Nuevo-Cliente
$idPrueba = $null
try {
    Titulo "1. Administrador"
    $c = Read-Host "  Contrasena de $Administrador" -AsSecureString
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($c)
    try { $r = Api $admin POST "/auth/login" @{ username = $Administrador; password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
    Exigir $r 200 "login administrador"
    $idAdmin = [long] $r.Datos.idUsuario
    Status (Api $admin GET "/roles") 200 "GET /roles"
    $suc = Api $admin GET "/sucursales"
    Status $suc 200 "GET /sucursales"
    $admins = Api $admin GET "/usuarios?rol=ADMINISTRADOR&estado=ACTIVO"
    Exigir $admins 200 "GET /usuarios (administradores activos)"
    Write-Host "  Administradores activos: $($admins.Datos.total)"

    Titulo "2. Validaciones al crear (no escriben)"
    $clave1 = Clave-Aleatoria
    Status (Api $admin POST "/usuarios" @{ nombre = "Prueba"; username = "prueba.sucursal"; rol = "SUCURSAL"; idSucursal = $null; password = $clave1 }) 400 "SUCURSAL sin sucursal"
    Status (Api $admin POST "/usuarios" @{ nombre = "Prueba"; username = "prueba.bodega"; rol = "BODEGA"; idSucursal = 1; password = $clave1 }) 400 "BODEGA con sucursal"
    Status (Api $admin POST "/usuarios" @{ nombre = "Prueba"; username = "Prueba.Mayus"; rol = "BODEGA"; idSucursal = $null; password = $clave1 }) 400 "username con mayusculas"
    Status (Api $admin POST "/usuarios" @{ nombre = "Prueba"; username = "prueba.corta"; rol = "BODEGA"; idSucursal = $null; password = "corta" }) 400 "contrasena corta"

    Titulo "3. Crear prueba.usuarios (BODEGA)"
    $r = Api $admin POST "/usuarios" @{ nombre = "Usuario de prueba"; username = $username; rol = "BODEGA"; idSucursal = $null; password = $clave1 }
    Exigir $r 201 "crear $username"
    $idPrueba = [long] $r.Datos.idUsuario
    Write-Host "  idUsuario = $idPrueba"
    $campos = ($r.Datos.PSObject.Properties.Name | Sort-Object) -join ","
    Ver "la respuesta no trae datos de la contrasena" (-not ($r.Texto -match '(?i)hash|salt|iteraciones|algoritmo|password')) $r.Texto
    Write-Host "  campos: $campos"
    Status (Api $admin POST "/usuarios" @{ nombre = "Otro"; username = $username; rol = "BODEGA"; idSucursal = $null; password = $clave1 }) 409 "username repetido"

    Titulo "4. Sesion del usuario de prueba"
    Exigir (Api $prueba POST "/auth/login" @{ username = $username; password = $clave1 }) 200 "login $username"
    $me = Api $prueba GET "/auth/me"
    Ver "rol BODEGA en /auth/me" ($me.Datos.rol -eq "BODEGA") $me.Texto
    Status (Api $prueba GET "/inventario/productos/33/existencia") 200 "BODEGA ve existencias"
    Status (Api $prueba GET "/pedidos/2") 403 "BODEGA no ve pedidos"
    Status (Api $prueba GET "/usuarios") 403 "BODEGA no administra usuarios"

    Titulo "5. Cambio de rol con la sesion abierta"
    $r = Api $admin PUT "/usuarios/$idPrueba" @{ nombre = "Usuario de prueba"; rol = "GERENTE"; idSucursal = $null }
    Exigir $r 200 "cambiar rol a GERENTE"
    $me = Api $prueba GET "/auth/me"
    Ver "la misma sesion ya es GERENTE" ($me.Datos.rol -eq "GERENTE") $me.Texto
    Status (Api $prueba GET "/pedidos/2") 200 "GERENTE si ve pedidos"

    Titulo "6. Bloqueo y desbloqueo"
    for ($i = 1; $i -le 5; $i++) { $ultimo = Api $otro POST "/auth/login" @{ username = $username; password = "incorrecta-$i-xxxx" } }
    Status $ultimo 401 "quinto intento fallido"
    $r = Api $admin GET "/usuarios/$idPrueba"
    Ver "bloqueado = true" ($r.Datos.bloqueado -eq $true) $r.Texto
    Status (Api $otro POST "/auth/login" @{ username = $username; password = $clave1 }) 401 "bloqueado: ni con la clave correcta"
    $r = Api $admin POST "/usuarios/$idPrueba/desbloquear"
    Exigir $r 200 "desbloquear"
    Ver "bloqueado = false" ($r.Datos.bloqueado -eq $false) $r.Texto

    Titulo "7. Cambiar mi propia contrasena"
    Exigir (Api $vieja POST "/auth/login" @{ username = $username; password = $clave1 }) 200 "segunda sesion (antes del cambio)"
    $clave2 = Clave-Aleatoria
    Status (Api $prueba PUT "/auth/clave" @{ claveActual = "no-es-la-clave"; claveNueva = $clave2 }) 400 "clave actual incorrecta"
    Status (Api $prueba PUT "/auth/clave" @{ claveActual = $clave1; claveNueva = $clave2 }) 200 "cambiar clave"
    Status (Api $prueba GET "/auth/me") 200 "la sesion actual sigue valida (renovada)"
    Status (Api $vieja GET "/auth/me") 401 "la otra sesion quedo invalidada"
    Status (Api $otro POST "/auth/login" @{ username = $username; password = $clave1 }) 401 "la clave anterior ya no sirve"
    Exigir (Api $otro POST "/auth/login" @{ username = $username; password = $clave2 }) 200 "la clave nueva funciona"

    Titulo "8. Ultimo administrador (rechazos, no cambian nada)"
    if ([long] $admins.Datos.total -eq 1) {
        Status (Api $admin PUT "/usuarios/$idAdmin/estado" @{ estado = "INACTIVO" }) 409 "desactivar al ultimo administrador"
        $yo = Api $admin GET "/usuarios/$idAdmin"
        Status (Api $admin PUT "/usuarios/$idAdmin" @{ nombre = $yo.Datos.nombre; rol = "GERENTE"; idSucursal = $null }) 409 "quitar el rol al ultimo administrador"
        Status (Api $admin GET "/auth/me") 200 "el administrador sigue activo"
    } else {
        Write-Host "  Hay mas de un administrador activo: se omite esta seccion." -ForegroundColor Yellow
    }

    Titulo "9. Desactivar prueba.usuarios"
    $r = Api $admin PUT "/usuarios/$idPrueba/estado" @{ estado = "INACTIVO" }
    Exigir $r 200 "desactivar"
    Status (Api $prueba GET "/auth/me") 401 "su sesion abierta se cierra"
    Status (Api $otro GET "/auth/me") 401 "y la otra tambien"
    Status (Api $vieja POST "/auth/login" @{ username = $username; password = $clave2 }) 401 "inactivo: no puede entrar"
    $lista = Api $admin GET "/usuarios?estado=INACTIVO&tamano=100"
    Ver "aparece en la lista de inactivos" (@($lista.Datos.items | Where-Object { $_.idUsuario -eq $idPrueba }).Count -eq 1)
} catch {
    $script:fallas++
    Write-Host "  FALLA  Paso interrumpido: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    foreach ($cl in $admin, $prueba, $vieja, $otro) { try { [void] (Api $cl POST "/auth/logout") } catch { }; $cl.Dispose() }
    Titulo "Resumen"
    Write-Host "  OK: $script:ok   Fallas: $script:fallas   (idUsuario de prueba: $idPrueba)"
}
