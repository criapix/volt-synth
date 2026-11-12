# Builds the engine and runs its tests on the PC.  Usage:  .\tools\test-engine.ps1 [filter]
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$root = Split-Path $PSScriptRoot -Parent
cmake -S "$root\engine" -B "$root\build\engine" -G Ninja -DCMAKE_BUILD_TYPE=Release -DVOLT_TESTS=ON
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
cmake --build "$root\build\engine"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$root\build\engine\volt_tests.exe" @args
exit $LASTEXITCODE
