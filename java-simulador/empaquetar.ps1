# Genera un ejecutable de Windows (SimuladorSolidos.exe) que NO requiere tener Java instalado.
# Uso:  powershell -ExecutionPolicy Bypass -File empaquetar.ps1
# Resultado: target\dist\SimuladorSolidos\SimuladorSolidos.exe  (copie la carpeta completa)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# JDK 21 (trae jpackage). Use JAVA_HOME si ya está definido; si no, el JDK que instala IntelliJ.
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -match "21" } | Select-Object -First 1
    if (-not $jdk) { throw "Defina JAVA_HOME apuntando a un JDK 21." }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "JDK: $env:JAVA_HOME"

# Maven: el del PATH o el que trae IntelliJ.
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Get-ChildItem "C:\Program Files\JetBrains\*\plugins\maven\lib\maven3\bin\mvn.cmd" -ErrorAction SilentlyContinue |
           Select-Object -First 1 -ExpandProperty FullName
    if (-not $mvn) { throw "No se encontró Maven (mvn)." }
}

& $mvn -q package
if ($LASTEXITCODE -ne 0) { throw "La compilación o las pruebas fallaron." }

$jar = "simulador-solidos-revolucion-1.0.0-shaded.jar"
$inputDir = "target\jpackage-input"
Remove-Item -Recurse -Force $inputDir, "target\dist" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $inputDir | Out-Null
Copy-Item "target\$jar" $inputDir

& "$env:JAVA_HOME\bin\jpackage.exe" `
    --type app-image `
    --name SimuladorSolidos `
    --app-version 1.0.0 `
    --vendor "UMG - Calculo II" `
    --input $inputDir `
    --main-jar $jar `
    --main-class com.umg.calculo.Launcher `
    --add-modules java.base,java.desktop,java.logging,jdk.unsupported `
    --jlink-options "--strip-debug --no-header-files --no-man-pages" `
    --dest target\dist
if ($LASTEXITCODE -ne 0) { throw "jpackage falló." }
# Solo los módulos de Java que usa la app (JavaFX va dentro del JAR): la carpeta pesa mucho menos.

# Un solo archivo para compartir: se descomprime y se abre SimuladorSolidos.exe, sin instalar nada.
Copy-Item "LEEME.txt" "target\dist\SimuladorSolidos\LEEME.txt"
$zip = "target\dist\SimuladorSolidos.zip"
# tar (incluido en Windows 10+) en vez de Compress-Archive: este último guarda rutas con "\",
# que algunos descompresores no entienden.
Remove-Item $zip -ErrorAction SilentlyContinue
tar -a -c -f $zip -C "target\dist" SimuladorSolidos
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el .zip." }

Write-Host ""
Write-Host "Listo: target\dist\SimuladorSolidos\SimuladorSolidos.exe"
Write-Host "Para compartir: $zip"
