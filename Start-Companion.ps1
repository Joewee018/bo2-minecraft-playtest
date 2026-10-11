$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$taskPrivate=Join-Path $taskRoot 'private'
$taskRuntime=Join-Path $taskPrivate 'runtime'
if(!(Test-Path -LiteralPath (Join-Path $taskRuntime 'local-bo2/weapons/weapon-catalog.txt'))){throw 'Run install.py with your converted local assets first.'}
$taskPort=28762
if(Get-NetTCPConnection -LocalPort $taskPort -State Listen -ErrorAction SilentlyContinue){throw 'Another local companion is using port 28762. Close it first.'}
$taskBytes=New-Object byte[] 32
$taskRng=[Security.Cryptography.RandomNumberGenerator]::Create();$taskRng.GetBytes($taskBytes);$taskRng.Dispose()
$taskToken=([BitConverter]::ToString($taskBytes)).Replace('-','').ToLowerInvariant()
$taskOldToken=$env:BO2_BETA_TOKEN;$taskOldPort=$env:BO2_BETA_PORT;$taskOldAssets=$env:BASEPLATE_ASSETS
try {
 $env:BO2_BETA_TOKEN=$taskToken;$env:BO2_BETA_PORT=[string]$taskPort;$env:BASEPLATE_ASSETS=$taskRuntime
 $taskArgs=Join-Path $taskPrivate 'jvm-arguments.txt'
 "-Dbo2beta.port=$taskPort -Dbo2beta.token=$taskToken" | Set-Content -LiteralPath $taskArgs
 $taskProcess=Start-Process -FilePath (Join-Path $taskRoot 'bo2-playtest-companion.exe') -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskPrivate 'companion.log') -RedirectStandardError (Join-Path $taskPrivate 'companion-error.log')
 Start-Sleep -Milliseconds 500
 if($taskProcess.HasExited){throw 'Companion failed. Inspect private/companion-error.log.'}
 Write-Host ('Companion ready. Copy the JVM arguments from '+$taskArgs+' into your Fabric installation, then launch Minecraft normally.')
 Read-Host 'Press Enter after Minecraft closes to stop this companion' | Out-Null
} finally {
 if($taskProcess -and !$taskProcess.HasExited){Stop-Process -Id $taskProcess.Id}
 $env:BO2_BETA_TOKEN=$taskOldToken;$env:BO2_BETA_PORT=$taskOldPort;$env:BASEPLATE_ASSETS=$taskOldAssets
}
