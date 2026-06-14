$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$project = Join-Path $PSScriptRoot "android\uploader"
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) {
    $bundledSdk = Join-Path $root "work\tools\android-sdk"
    if (Test-Path $bundledSdk) {
        $sdk = $bundledSdk
    } else {
        throw "Set ANDROID_SDK_ROOT to an Android SDK that contains build-tools\35.0.0 and platforms\android-35."
    }
}
$bt = Join-Path $sdk "build-tools\35.0.0"
$androidJar = Join-Path $sdk "platforms\android-35\android.jar"
$build = Join-Path $PSScriptRoot "build-uploader-manual"
$out = Join-Path $root "outputs\LiveCircleUploader.apk"

if (-not $env:JAVA_HOME) {
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $javac) {
        throw "Set JAVA_HOME to a JDK 17+ installation or add javac.exe to PATH."
    }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javac.Source)
}
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$env:JAVA_HOME\bin;$sdk\cmdline-tools\latest\bin;$sdk\platform-tools;$env:PATH"

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$build\compiled", "$build\gen", "$build\obj", "$build\dex" | Out-Null

& (Join-Path $bt "aapt2.exe") compile --dir (Join-Path $project "src\main\res") -o (Join-Path $build "compiled\res.zip")
& (Join-Path $bt "aapt2.exe") link -I $androidJar --manifest (Join-Path $project "src\main\AndroidManifest.xml") --java (Join-Path $build "gen") -o (Join-Path $build "unsigned.apk") (Join-Path $build "compiled\res.zip") --auto-add-overlay

$sources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $project "src\main\java") | ForEach-Object FullName) + @(Get-ChildItem -Recurse -Filter *.java (Join-Path $build "gen") | ForEach-Object FullName)
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -classpath $androidJar -d (Join-Path $build "obj") @sources

$classFiles = @(Get-ChildItem -Recurse -Filter *.class (Join-Path $build "obj") | ForEach-Object FullName)
& (Join-Path $bt "d8.bat") --lib $androidJar --output (Join-Path $build "dex") @classFiles

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = Join-Path $build "unsigned.apk"
$zip = [System.IO.Compression.ZipFile]::Open($apkPath, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $zip.GetEntry("classes.dex")
    if ($existing) { $existing.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $build "dex\classes.dex"), "classes.dex", [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
} finally {
    $zip.Dispose()
}

$keystore = Join-Path $PSScriptRoot "livecircle-debug.keystore"
if (-not (Test-Path $keystore)) {
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore $keystore -storepass android -keypass android -alias livecircle -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LiveCircle,O=Codex,C=CN"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $out) | Out-Null
& (Join-Path $bt "zipalign.exe") -f -p 4 $apkPath (Join-Path $build "LiveCircleUploader-aligned.apk")
& (Join-Path $bt "apksigner.bat") sign --ks $keystore --ks-key-alias livecircle --ks-pass pass:android --key-pass pass:android --out $out (Join-Path $build "LiveCircleUploader-aligned.apk")
& (Join-Path $bt "apksigner.bat") verify --verbose $out
Get-Item $out
