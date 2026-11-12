# Dot-source before any PC build:  . .\tools\env.ps1
# Forces the VS 2022 Build Tools MSVC environment (the machine also has a VS 18 Community
# without the C++ CRT libs, which CMake would otherwise pick and fail with LNK1104 msvcrt.lib).
$vc = "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cmd /c "`"$vc`" >nul 2>&1 && set" | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2] }
}
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
