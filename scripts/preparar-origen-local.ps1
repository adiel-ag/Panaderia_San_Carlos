param(
    [string] $TomcatHome = 'C:\Users\Angel Gomez\Downloads\apache-tomcat-10.1.61\apache-tomcat-10.1.61'
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$base = Join-Path $raiz 'target\publicacion-tomcat'
$war = Join-Path $raiz 'target\panaderia-san-carlos-1.0.0-SNAPSHOT.war'
if (-not (Test-Path -LiteralPath (Join-Path $TomcatHome 'bin\startup.bat'))) {
    throw 'No se encontró Tomcat 10.1 en la ruta indicada.'
}
if (-not (Test-Path -LiteralPath $war)) { throw 'Primero construya el WAR de producción.' }
& (Join-Path $PSScriptRoot 'verificar-war-produccion.ps1') -WarPath $war

if (-not (Test-Path -LiteralPath (Join-Path $base 'conf\server.xml'))) {
    New-Item -ItemType Directory -Path $base -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $TomcatHome 'conf') -Destination $base -Recurse
}
foreach ($carpeta in @('webapps', 'logs', 'temp', 'work')) {
    New-Item -ItemType Directory -Path (Join-Path $base $carpeta) -Force | Out-Null
}

$config = Join-Path $base 'conf\server.xml'
[xml] $xml = Get-Content -LiteralPath $config
$xml.Server.SetAttribute('port', '8006')
$conectores = @($xml.Server.Service.Connector | Where-Object { $_.port -eq '8080' -or $_.port -eq '8081' })
if ($conectores.Count -ne 1) { throw 'No se encontró un único conector HTTP de Tomcat.' }
$conectores[0].SetAttribute('port', '8081')
$conectores[0].SetAttribute('address', '127.0.0.1')
$hostLocal = @($xml.Server.Service.Engine.Host | Where-Object { $_.name -eq 'localhost' })
if ($hostLocal.Count -ne 1) { throw 'No se encontró el host localhost de Tomcat.' }
$hostLocal[0].SetAttribute('autoDeploy', 'false')
$xml.Save($config)

# NetBeans puede dejar un ROOT.xml que apunta a otro docBase y hace que
# Tomcat ignore el ROOT.war. Solo se desactiva la copia dentro de target/.
$contextos = Join-Path $base 'conf\Catalina\localhost'
$rootContexto = Join-Path $contextos 'ROOT.xml'
if (Test-Path -LiteralPath $rootContexto) {
    Rename-Item -LiteralPath $rootContexto -NewName 'ROOT.xml.disabled'
}
$otrosContextos = @(Get-ChildItem -LiteralPath $contextos -Filter '*.xml' -ErrorAction SilentlyContinue)
if ($otrosContextos.Count -gt 0) { throw 'La instancia pública contiene contextos adicionales.' }

$webapps = Join-Path $base 'webapps'
$otros = @(Get-ChildItem -LiteralPath $webapps | Where-Object { $_.Name -ne 'ROOT.war' -and $_.Name -ne 'ROOT' })
if ($otros.Count -gt 0) { throw 'La instancia pública contiene aplicaciones distintas de ROOT.' }
Copy-Item -LiteralPath $war -Destination (Join-Path $webapps 'ROOT.war') -Force
Write-Output "Origen preparado en $base; solo escucha en 127.0.0.1:8081 al iniciarse."
