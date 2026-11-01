# Copia la base local (XE) al esquema PANADERIA de Oracle Cloud, de principio a fin:
#   1. database/nube/00_limpiar_nube.sql   (borra solo objetos de la panaderia en la nube)
#   2. database/nube/01_crear_esquema_nube.sql
#   3. CopiarDatosNube.java                (lee XE en solo lectura, copia en una transaccion)
#   4. database/nube/02_finalizar_nube.sql (llaves foraneas y contadores de ID)
#   5. Compara las filas de cada tabla entre XE y la nube.
# La base local nunca se modifica. Las contrasenas no se imprimen.
# Requiere en .env: PSC_CLOUD_PASSWORD y PSC_XE_DB_URL/USER/PASSWORD (origen local). Opcionales: PSC_CLOUD_WALLET_DIR, PSC_CLOUD_SERVICIO.
# Si las tablas de la nube ya tienen datos, el script se niega a borrarlas salvo
# con -Reemplazar. Asi, si la copia falla despues de limpiar, no se pierde nada
# que no exista tambien en la base local.
param([switch] $Reemplazar)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

$envArchivo = @{}
$rutaEnv = Join-Path $raiz '.env'
if (Test-Path -LiteralPath $rutaEnv) {
    foreach ($linea in Get-Content -LiteralPath $rutaEnv) {
        if ($linea -notmatch '^\s*#' -and $linea -match '^\s*([A-Z_]+)\s*=(.*)$') { $envArchivo[$Matches[1]] = $Matches[2].Trim() }
    }
}
function Valor([string] $nombre, [string] $porDefecto = $null) {
    foreach ($v in @([Environment]::GetEnvironmentVariable($nombre, 'Process'), $envArchivo[$nombre],
                     [Environment]::GetEnvironmentVariable($nombre, 'User'),
                     [Environment]::GetEnvironmentVariable($nombre, 'Machine'))) {
        if ($v) { return $v }
    }
    return $porDefecto
}

$wallet = Valor 'PSC_CLOUD_WALLET_DIR' 'C:\oracle\wallet_panaderia'
$servicio = Valor 'PSC_CLOUD_SERVICIO' 'panaderiasancarlos_tp'
$clave = Valor 'PSC_CLOUD_PASSWORD'
if (-not $clave) { throw 'Falta PSC_CLOUD_PASSWORD en el archivo .env.' }
if (-not (Test-Path -LiteralPath (Join-Path $wallet 'cwallet.sso'))) { throw "No se encontro el wallet en $wallet." }

$env:TNS_ADMIN = $wallet
$env:NLS_LANG = 'SPANISH_SPAIN.AL32UTF8'
# Origen: el respaldo de la base local (PSC_XE_DB_*), nunca DB_URL, que ya
# puede apuntar a la nube. CopiarDatosNube.java se niega si el origen es la nube.
foreach ($n in 'PSC_XE_DB_URL', 'PSC_XE_DB_USER', 'PSC_XE_DB_PASSWORD') {
    $v = Valor $n
    if (-not $v) { throw "Falta $n en .env (conexion a la base local de origen). No se toco nada." }
    Set-Item "env:$n" $v
}
if ($env:PSC_XE_DB_URL -match 'TNS_ADMIN') { throw 'PSC_XE_DB_URL parece de Oracle Cloud; debe ser la base local. No se toco nada.' }
$env:PSC_CLOUD_URL = "jdbc:oracle:thin:@${servicio}?TNS_ADMIN=$($wallet -replace '\\','/')"
$env:PSC_CLOUD_USER = 'PANADERIA'
$env:PSC_CLOUD_PASSWORD = $clave

function SqlNube([string] $script) {
    Push-Location (Join-Path $raiz 'database\nube')
    try {
        # La primera linea es relleno: PowerShell 5.1 antepone un BOM a la entrada.
        "REM inicio`nWHENEVER SQLERROR EXIT 1`nconnect PANADERIA/`"$clave`"@$servicio`n@$script`nexit" | & sqlplus -s -L /nolog
        if ($LASTEXITCODE -ne 0) { throw "$script termino con error ($LASTEXITCODE)." }
    } finally { Pop-Location }
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { $null }
if (-not $java -or -not (Test-Path -LiteralPath $java)) { throw 'No se encontro Java (JAVA_HOME). No se toco nada.' }
$ojdbc = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\com\oracle\database\jdbc\ojdbc17') -Recurse -Filter 'ojdbc17-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $ojdbc) { throw 'No se encontro el driver ojdbc17 (compile el proyecto con Maven). No se toco nada.' }
# oraclepki permite al driver leer el wallet (cwallet.sso) de Oracle Cloud.
$pki = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\com\oracle\database\security\oraclepki') -Recurse -Filter 'oraclepki-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $pki) { throw 'Falta oraclepki: mvn dependency:get -Dartifact=com.oracle.database.security:oraclepki:23.26.3.0.0. No se toco nada.' }
if (-not (Get-Command sqlplus -ErrorAction SilentlyContinue)) { throw 'No se encontro sqlplus. No se toco nada.' }
function Copiador([string[]] $argumentos) {
    Push-Location $raiz
    try {
        & $java -cp "$($ojdbc.FullName);$($pki.FullName)" (Join-Path $PSScriptRoot 'CopiarDatosNube.java') @argumentos | Out-Host
        return $LASTEXITCODE
    } finally { Pop-Location }
}

# Antes de borrar nada en la nube: Java, driver, conexion local (solo lectura),
# conexion a la nube como PANADERIA y las 17 tablas locales deben funcionar.
Write-Host '=== 0/4 Verificacion previa (no escribe nada)'
$argsVerificar = @('--verificar'); if ($Reemplazar) { $argsVerificar += '--reemplazar' }
if ((Copiador $argsVerificar) -ne 0) { throw 'La verificacion previa fallo. No se toco nada en la nube.' }
$prueba = "REM inicio`nWHENEVER SQLERROR EXIT 1`nconnect PANADERIA/`"$clave`"@$servicio`nselect 'SQLPLUS_' || 'CONECTADO' from dual;`nexit" | & sqlplus -s -L /nolog
if ($LASTEXITCODE -ne 0 -or -not ($prueba -match 'SQLPLUS_CONECTADO')) { throw 'sqlplus no pudo conectarse a la nube. No se toco nada.' }

Write-Host '=== 1/4 Limpiando objetos de la panaderia en la nube'
SqlNube '00_limpiar_nube.sql'
Write-Host '=== 2/4 Creando tablas y paquetes en la nube'
SqlNube '01_crear_esquema_nube.sql'

Write-Host '=== 3/4 Copiando datos (la base local solo se lee)'
if ((Copiador @()) -ne 0) { throw 'La copia de datos fallo; la nube quedo sin datos (se revirtio).' }

Write-Host '=== 4/4 Activando llaves y ajustando contadores de ID'
SqlNube '02_finalizar_nube.sql'
Write-Host '=== Migracion terminada'
