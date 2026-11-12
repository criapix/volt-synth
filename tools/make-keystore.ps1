# Creates the release signing key (run once, by the user: keytool asks for the passwords).
# Then write keystore.properties in the repository root (never committed):
#   storeFile=C:/Users/<you>/.android/volt-release.jks
#   storePassword=...
#   keyAlias=volt
#   keyPassword=...
# KEEP A BACKUP of the .jks and the passwords: without them no future version can update the installed app.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\env.ps1"
$jks = Join-Path $env:USERPROFILE ".android\volt-release.jks"
if (Test-Path $jks) { "Already exists: $jks"; exit 0 }
New-Item -ItemType Directory -Force (Split-Path $jks) | Out-Null
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $jks -alias volt -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Volt"
"Created $jks. Now write keystore.properties (see the top of this script)."
