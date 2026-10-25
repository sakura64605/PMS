param([switch]$SkipTests,[string]$Settings)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$argsList=@()
if ($Settings) { $argsList += @('-s',$Settings) }
$argsList += 'package'
if ($SkipTests) { $argsList += '-DskipTests' }
Push-Location $root
try { & mvn @argsList; if ($LASTEXITCODE -ne 0) { throw 'Backend reactor build failed' } }
finally { Pop-Location }
