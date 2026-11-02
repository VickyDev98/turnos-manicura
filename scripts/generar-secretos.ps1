# Crea la carpeta secrets/ con un archivo por secreto. docker-compose los monta como Docker secrets
# (/run/secrets/...), así las claves ya no viajan como variables de entorno de los contenedores.
#
# Uso (desde la raíz del proyecto):   powershell -ExecutionPolicy Bypass -File scripts\generar-secretos.ps1
#
# - No pisa archivos que ya existen.
# - Toma los valores del .env si los hay. Esto es IMPRESCINDIBLE para la clave de MySQL si ya tenés una base creada:
#   MySQL solo toma la clave root cuando crea el volumen por primera vez; una clave distinta no entraría.
# - Si un valor no está en el .env, genera uno al azar (la clave de MySQL solo sirve así en una base nueva).

$raiz = Split-Path -Parent $PSScriptRoot
$carpeta = Join-Path $raiz 'secrets'
New-Item -ItemType Directory -Force $carpeta | Out-Null

# Lee KEY=valor del .env (ignora comentarios y líneas vacías)
$env_ = @{}
$archivoEnv = Join-Path $raiz '.env'
if (Test-Path $archivoEnv) {
    foreach ($linea in Get-Content $archivoEnv -Encoding UTF8) {
        if ($linea -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { $env_[$Matches[1]] = $Matches[2] }
    }
}

function Clave-Al-Azar([int]$bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    # Solo letras y números: sin caracteres que compliquen el shell o los archivos de configuración
    ([Convert]::ToBase64String($b) -replace '[^A-Za-z0-9]', '')
}

# Sin BOM y sin salto de línea final: el archivo contiene exactamente el secreto
function Guardar([string]$nombre, [string]$valor) {
    [IO.File]::WriteAllText((Join-Path $carpeta $nombre), $valor, (New-Object Text.UTF8Encoding($false)))
}

function Crear-Secreto([string]$archivo, [string]$variableEnv, [scriptblock]$alAzar, [int]$minimo = 0) {
    $ruta = Join-Path $carpeta $archivo
    if (Test-Path $ruta) { Write-Host "  = $archivo ya existe, no se toca"; return }
    $valor = $env_[$variableEnv]
    $origen = "tomado del .env ($variableEnv)"
    if ([string]::IsNullOrEmpty($valor) -and $null -ne $alAzar) { $valor = & $alAzar; $origen = 'generado al azar' }
    if ($null -eq $valor) { $valor = ''; $origen = 'vacío (opcional)' }
    if ($minimo -gt 0 -and $valor.Length -lt $minimo) {
        Write-Warning "$archivo tiene menos de $minimo caracteres: el backend no va a arrancar con esa clave."
    }
    Guardar $archivo $valor
    Write-Host "  + $archivo creado ($origen)"
}

Write-Host "Creando secretos en $carpeta"
Crear-Secreto 'mysql_root_password.txt' 'MYSQL_ROOT_PASSWORD' { Clave-Al-Azar 24 }
Crear-Secreto 'jwt_secret.txt' 'JWT_SECRET' { Clave-Al-Azar 48 } 32
Crear-Secreto 'smtp_password.txt' 'SMTP_PASSWORD' $null

Write-Host ''
Write-Host 'Listo. Después podés borrar MYSQL_ROOT_PASSWORD, JWT_SECRET y SMTP_PASSWORD del .env (ya no se usan ahí).'
if (-not $env_.ContainsKey('MYSQL_ROOT_PASSWORD')) {
    Write-Warning 'No había MYSQL_ROOT_PASSWORD en el .env: la clave de MySQL es nueva y solo sirve para una base nueva (docker compose down -v).'
}
