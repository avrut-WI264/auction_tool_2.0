param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env'),
    [string]$OutputFile = (Join-Path $PSScriptRoot ('../.local-backups/auction_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.dump'))
)
$ErrorActionPreference = 'Stop'
$settings = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
}
foreach ($key in @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','APP_DATABASE_SCHEMA')) {
    $override = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ($override) { $settings[$key] = $override }
    if (-not $settings[$key]) { throw "Missing $key" }
}
$uri = [Uri]($settings['SPRING_DATASOURCE_URL'] -replace '^jdbc:', '')
$schema = $settings['APP_DATABASE_SCHEMA']
if ($schema -notmatch '^[a-z][a-z0-9_]{0,62}$' -or $schema -eq 'public') { throw 'A dedicated application schema is required.' }
$destination = [IO.Path]::GetFullPath($OutputFile)
if (Test-Path -LiteralPath $destination) { throw 'Backup destination already exists; refusing to overwrite it.' }
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
$command = Get-Command pg_dump.exe -ErrorAction SilentlyContinue
$pgDump = if ($command) { $command.Source } else { Join-Path $env:ProgramFiles 'PostgreSQL/18/bin/pg_dump.exe' }
if (-not (Test-Path -LiteralPath $pgDump)) { throw 'Install PostgreSQL client tools and add pg_dump to PATH.' }
$oldPassword = $env:PGPASSWORD
$oldSslMode = $env:PGSSLMODE
try {
    $env:PGPASSWORD = $settings['SPRING_DATASOURCE_PASSWORD']
    if ($uri.Query -match '(?:[?&])sslmode=([^&]+)') { $env:PGSSLMODE = $matches[1] }
    $port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
    & $pgDump '-h' $uri.Host '-p' $port '-U' $settings['SPRING_DATASOURCE_USERNAME'] '-d' $uri.AbsolutePath.TrimStart('/') '-n' $schema '--format=custom' '--no-owner' '--no-privileges' '--file' $destination
    if ($LASTEXITCODE -ne 0) { throw 'Backup failed.' }
    Write-Output ('Backup written to ' + $destination)
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $oldPassword, 'Process')
    [Environment]::SetEnvironmentVariable('PGSSLMODE', $oldSslMode, 'Process')
}
