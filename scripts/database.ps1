param(
    [Parameter(Mandatory=$true)][ValidateSet('migrate','provision-admin')][string]$Operation,
    [string]$EnvFile = (Join-Path $PSScriptRoot '../.env'),
    [string]$City,
    [string]$Username,
    [switch]$BaselineExisting,
    [switch]$AllowReset
)
$ErrorActionPreference = 'Stop'
$previous = @{}
function Set-TaskVariable([string]$Name, [string]$Value) {
    if (-not $previous.ContainsKey($Name)) { $previous[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
try {
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            if (-not [Environment]::GetEnvironmentVariable($matches[1], 'Process')) {
                Set-TaskVariable $matches[1] $matches[2].Trim().Trim('"').Trim("'")
            }
        }
    }
    Set-TaskVariable 'APP_ALLOW_BASELINE_EXISTING' $BaselineExisting.IsPresent.ToString().ToLowerInvariant()
    if ($Operation -eq 'provision-admin') {
        if (-not $City -or -not $Username) { throw 'City and Username are required.' }
        Set-TaskVariable 'BOOTSTRAP_CITY' $City
        Set-TaskVariable 'BOOTSTRAP_USERNAME' $Username
        Set-TaskVariable 'BOOTSTRAP_ALLOW_RESET' $AllowReset.IsPresent.ToString().ToLowerInvariant()
        $securePassword = Read-Host 'New administrator password (12+ characters)' -AsSecureString
        $credential = New-Object System.Management.Automation.PSCredential('bootstrap', $securePassword)
        Set-TaskVariable 'BOOTSTRAP_PASSWORD' $credential.GetNetworkCredential().Password
    }
    $backend = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../auction-backend')).Path
    Push-Location -LiteralPath $backend
    try {
        & mvn.cmd -q '-DskipTests' compile dependency:build-classpath '-Dmdep.outputFile=target/tool-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Database tool compilation failed.' }
        $dependencies = (Get-Content -LiteralPath 'target/tool-classpath.txt' -Raw).Trim()
        $classes = Join-Path $backend 'target/classes'
        & java.exe '-cp' ($classes + [IO.Path]::PathSeparator + $dependencies) 'com.wissen.auction.ops.DatabaseTool' $Operation
        if ($LASTEXITCODE -ne 0) { throw 'Database operation failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
