#Requires -RunAsAdministrator
<#
  Prepara TU SQL Server local para la API de DateManager. Ejecútalo como administrador.

  Qué cambia en tu equipo (nada más):
    1. Habilita TCP/IP en la instancia (puerto fijo 1433) y pasa a autenticación mixta (Windows + SQL).
    2. Reinicia el servicio de esa instancia (se cae unos segundos).
    3. Crea las bases DateManager y DateManagerTest (si no existen).
    4. Crea el login SQL "datemanager_app" con la contraseña que escribas, dueño SOLO de esas dos bases.
    5. Escribe src/Date.Manager.Back/.env.local (ignorado por git) con las variables de conexión y un secreto JWT aleatorio.

  No toca otras bases (Nogal, NogalTest...). La contraseña la escribes tú aquí y no pasa por el chat.
#>
$ErrorActionPreference = 'Stop'

$instancia = 'MSSQLSERVER01'
$servicio  = "MSSQL`$$instancia"
$puerto    = 1433
$login     = 'datemanager_app'
$raiz      = Split-Path -Parent $PSScriptRoot          # src/Date.Manager.Back

Write-Host "Se va a modificar la instancia .\$instancia: TCP/IP en el puerto $puerto, autenticación mixta y reinicio del servicio." -ForegroundColor Yellow
if ((Read-Host 'Escribe SI para continuar') -ne 'SI') { Write-Host 'Cancelado, no se cambió nada.'; return }

$segura = Read-Host "Contraseña nueva para el login $login (mínimo 12 caracteres, con mayúsculas, minúsculas y números; sin apóstrofos)" -AsSecureString
$clave  = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
if ($clave.Length -lt 12 -or $clave.Contains("'")) { throw 'Contraseña no válida: mínimo 12 caracteres y sin apóstrofos.' }

# 1. TCP/IP fijo y autenticación mixta
$id   = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\Instance Names\SQL').$instancia
$base = "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\$id\MSSQLServer"
Set-ItemProperty $base -Name LoginMode -Value 2
$tcp = "$base\SuperSocketNetLib\Tcp"
Set-ItemProperty $tcp -Name Enabled -Value 1
Set-ItemProperty "$tcp\IPAll" -Name TcpPort -Value "$puerto"
Set-ItemProperty "$tcp\IPAll" -Name TcpDynamicPorts -Value ''

# 2. Reinicio (y vuelve a arrancar lo que dependa de la instancia, por ejemplo el Agente)
$dependientes = @(Get-Service $servicio).DependentServices | Where-Object { $_.Status -eq 'Running' } | ForEach-Object { $_.Name }
Restart-Service $servicio -Force
foreach ($d in $dependientes) { Start-Service $d -ErrorAction SilentlyContinue }

$conectado = $false
for ($i = 0; $i -lt 30 -and -not $conectado; $i++) {
    & sqlcmd -S ".\$instancia" -E -b -Q 'SELECT 1' *> $null
    if ($LASTEXITCODE -eq 0) { $conectado = $true } else { Start-Sleep 2 }
}
if (-not $conectado) { throw 'El servicio no respondió después del reinicio.' }

# 3 y 4. Bases y login
$archivo = Join-Path $env:TEMP 'datemanager-preparar.sql'
@"
IF DB_ID(N'DateManager') IS NULL CREATE DATABASE [DateManager];
IF DB_ID(N'DateManagerTest') IS NULL CREATE DATABASE [DateManagerTest];
IF SUSER_ID(N'$login') IS NULL CREATE LOGIN [$login] WITH PASSWORD = N'$clave', CHECK_POLICY = ON;
ELSE ALTER LOGIN [$login] WITH PASSWORD = N'$clave';
GO
USE [DateManager];
IF USER_ID(N'$login') IS NULL CREATE USER [$login] FOR LOGIN [$login];
ALTER ROLE db_owner ADD MEMBER [$login];
GO
USE [DateManagerTest];
IF USER_ID(N'$login') IS NULL CREATE USER [$login] FOR LOGIN [$login];
ALTER ROLE db_owner ADD MEMBER [$login];
GO
"@ | Set-Content -Path $archivo -Encoding UTF8
try { & sqlcmd -S ".\$instancia" -E -b -i $archivo; if ($LASTEXITCODE -ne 0) { throw 'sqlcmd falló al crear las bases o el login.' } }
finally { Remove-Item $archivo -Force -ErrorAction SilentlyContinue }

# 5. Variables para la API (archivo ignorado por git)
$jwt = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 } | ForEach-Object { [byte]$_ }))
$url  = "jdbc:sqlserver://localhost:$puerto;encrypt=true;trustServerCertificate=true;databaseName="
$env_ = @"
DATEMANAGER_DB_URL=${url}DateManager
DATEMANAGER_DB_USUARIO=$login
DATEMANAGER_DB_CLAVE=$clave
DATEMANAGER_TEST_DB_URL=${url}DateManagerTest
DATEMANAGER_TEST_DB_USUARIO=$login
DATEMANAGER_TEST_DB_CLAVE=$clave
DATEMANAGER_JWT_SECRETO=$jwt
"@
[IO.File]::WriteAllText((Join-Path $raiz '.env.local'), $env_, (New-Object Text.UTF8Encoding $false))

# Comprobación por TCP con el login nuevo
& sqlcmd -S "localhost,$puerto" -U $login -P $clave -d DateManagerTest -C -b -Q 'SELECT DB_NAME() AS base' | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar por TCP con el login nuevo.' }
Write-Host "Listo. Variables escritas en $raiz\.env.local (no se sube a git)." -ForegroundColor Green
