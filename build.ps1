param(
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$XposedApi,
    [Parameter(Mandatory=$true)][string]$Jdk,
    [string]$BuildDir = "$PSScriptRoot/build",
    [string]$OutputApk = "$PSScriptRoot/WifiMacOverride.apk"
)
$ErrorActionPreference = 'Stop'
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$BuildDir = [IO.Path]::GetFullPath($BuildDir)
# Each build uses a fresh directory; no recursive deletion.
$run = Join-Path $BuildDir ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $run,"$run/classes","$run/dex" | Out-Null
& "$BuildTools/aapt2.exe" compile --dir "$PSScriptRoot/res" -o "$run/res.zip"
Check-Exit
& "$BuildTools/aapt2.exe" link -I $AndroidJar --manifest "$PSScriptRoot/AndroidManifest.xml" -A "$PSScriptRoot/assets" -o "$run/base.apk" "$run/res.zip"
Check-Exit
$sources = @(Get-ChildItem "$PSScriptRoot/src" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath "$AndroidJar;$XposedApi" -d "$run/classes" @sources
Check-Exit
& "$Jdk/bin/jar.exe" cf "$run/classes.jar" -C "$run/classes" .
Check-Exit
& "$Jdk/bin/java.exe" -cp "$BuildTools/lib/d8.jar" com.android.tools.r8.D8 --min-api 28 --lib $AndroidJar --classpath $XposedApi --output "$run/dex" "$run/classes.jar"
Check-Exit
Copy-Item "$run/base.apk" "$run/unsigned.apk"
& "$Jdk/bin/jar.exe" uf "$run/unsigned.apk" -C "$run/dex" classes.dex
Check-Exit
& "$BuildTools/zipalign.exe" -f 4 "$run/unsigned.apk" "$run/aligned.apk"
Check-Exit
$key = Join-Path $BuildDir 'local-test-signing.p12'
if (!(Test-Path $key)) {
    & "$Jdk/bin/keytool.exe" -genkeypair -keystore $key -storepass android -keypass android -alias localtest -dname 'CN=Local Experimental Module' -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12
    Check-Exit
}
& "$Jdk/bin/java.exe" -jar "$BuildTools/lib/apksigner.jar" sign --ks $key --ks-key-alias localtest --ks-pass pass:android --key-pass pass:android --out $OutputApk "$run/aligned.apk"
Check-Exit
& "$Jdk/bin/java.exe" -jar "$BuildTools/lib/apksigner.jar" verify --verbose $OutputApk
Check-Exit
Get-FileHash $OutputApk -Algorithm SHA256
