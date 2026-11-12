# Copies the Volt log from the phone (USB debugging on) into .\logs\ with a timestamp.
# Usage:  .\tools\pull-log.ps1                  (debug build, com.criapix.volt.debug)
#         .\tools\pull-log.ps1 -Package <id>
# A release build is not debuggable (no run-as): use the menu, "Salvar log...", in the app instead.
param([string]$Package = "com.criapix.volt.debug")
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root "logs"
New-Item -ItemType Directory -Force $out | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$app = $Package
foreach ($name in "volt-log.txt", "volt-log.old.txt") {
  $dest = Join-Path $out "$stamp-$name"
  # cmd handles the redirect so the bytes are not re-encoded by PowerShell
  cmd /c "adb exec-out run-as $app cat files/$name > `"$dest`" 2>nul"
  # a missing file makes `cat` write its error text into the redirect; drop that
  if ((Test-Path $dest) -and (Get-Item $dest).Length -gt 0 -and -not ((Get-Content $dest -TotalCount 1) -like "cat:*")) { "$dest ($((Get-Item $dest).Length) bytes)" }
  else { Remove-Item $dest -ErrorAction SilentlyContinue }
}
$lc = Join-Path $out "$stamp-logcat.txt"
cmd /c "adb logcat -d -s volt AndroidRuntime > `"$lc`""
"$lc ($((Get-Item $lc).Length) bytes)"
