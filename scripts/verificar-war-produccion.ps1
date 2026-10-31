param(
    [string] $WarPath = "target/panaderia-san-carlos-1.0.0-SNAPSHOT.war"
)

$ErrorActionPreference = "Stop"
$ruta = (Resolve-Path -LiteralPath $WarPath).Path
Add-Type -AssemblyName System.IO.Compression
# En Windows PowerShell 5.1, ZipFile vive en un ensamblado aparte.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archivo = [System.IO.Compression.ZipFile]::OpenRead($ruta)
try {
    $entradas = @($archivo.Entries | ForEach-Object FullName)
    foreach ($nombre in @("index.html", "WEB-INF/web.xml", "WEB-INF/classes/gt/edu/umg/sancarlos/resource/RestApplication.class")) {
        if ($nombre -notin $entradas) { throw "Falta $nombre en el WAR" }
    }

    $descriptor = $archivo.GetEntry("WEB-INF/web.xml")
    $lector = [System.IO.StreamReader]::new($descriptor.Open())
    try { $contenido = $lector.ReadToEnd() } finally { $lector.Dispose() }
    [xml] $xml = $contenido
    $sesion = $xml.GetElementsByTagName("session-config")[0]
    $cookie = $sesion.GetElementsByTagName("cookie-config")[0]
    if ($cookie.GetElementsByTagName("secure")[0].InnerText -ne "true") {
        throw "La cookie de producción no tiene Secure=true"
    }
    if ($cookie.GetElementsByTagName("http-only")[0].InnerText -ne "true") {
        throw "La cookie de producción no tiene HttpOnly=true"
    }
    $atributos = @($cookie.GetElementsByTagName("attribute"))
    $sameSite = $atributos | Where-Object { $_.GetElementsByTagName("attribute-name")[0].InnerText -eq "SameSite" }
    if ($sameSite.GetElementsByTagName("attribute-value")[0].InnerText -ne "Strict") {
        throw "La cookie de producción no tiene SameSite=Strict"
    }
    Write-Output "WAR de producción verificado: frontend, API y cookie segura."
} finally {
    $archivo.Dispose()
}
