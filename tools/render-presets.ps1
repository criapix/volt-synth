# Renders every factory preset to build\render\*.wav and opens the listening page.
# Usage:  .\tools\render-presets.ps1            (WAVs + index.html)
#         .\tools\render-presets.ps1 -Stress    (CPU cost per preset, 16 notes held)
param([switch]$Stress, [switch]$NoOpen)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$root = Split-Path $PSScriptRoot -Parent
cmake -S "$root\engine" -B "$root\build\engine" -G Ninja -DCMAKE_BUILD_TYPE=Release -DVOLT_TESTS=ON
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
cmake --build "$root\build\engine" --target volt_render
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$out = "$root\build\render"
if ($Stress) { & "$root\build\engine\volt_render.exe" $out --stress; exit $LASTEXITCODE }
& "$root\build\engine\volt_render.exe" $out
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (-not $NoOpen) { Start-Process "$out\index.html" }
