# Stores the release signing key in the GitHub repository's secrets, so the "Release APK" workflow can sign.
# Run once, on your machine, after tools\make-keystore.ps1 and after creating keystore.properties
# (see the top of make-keystore.ps1). The passwords never appear on a command line or in a log:
# they go to `gh secret set` through standard input.
#   .\tools\set-release-secrets.ps1 [-Repo criapix/volt-synth]
param([string]$Repo = "criapix/volt-synth")
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$propsFile = Join-Path $root "keystore.properties"
if (-not (Test-Path $propsFile)) {
  Write-Error "keystore.properties not found in $root. Run tools\make-keystore.ps1 and create it first (model at the top of that script)."
}
$p = @{}
Get-Content $propsFile | ForEach-Object {
  if ($_ -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*)$') { $p[$Matches[1]] = $Matches[2] }
}
foreach ($k in "storeFile", "storePassword", "keyAlias", "keyPassword") {
  if (-not $p.ContainsKey($k) -or [string]::IsNullOrEmpty($p[$k])) { Write-Error "keystore.properties has no $k" }
}
if (-not (Test-Path $p.storeFile)) { Write-Error "Key file not found: $($p.storeFile)" }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Write-Error "GitHub CLI (gh) not found; install it and run 'gh auth login'." }

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($p.storeFile))
$b64             | gh secret set KEYSTORE_BASE64   --repo $Repo
$p.storePassword | gh secret set KEYSTORE_PASSWORD --repo $Repo
$p.keyAlias      | gh secret set KEY_ALIAS         --repo $Repo
$p.keyPassword   | gh secret set KEY_PASSWORD      --repo $Repo
"Secrets set on $Repo. Back up $($p.storeFile) and its passwords somewhere safe:"
"without them no future version can update an app that was installed from a release."
