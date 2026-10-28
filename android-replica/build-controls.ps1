$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = Get-Content (Join-Path $root 'version.json') -Raw | ConvertFrom-Json
$sdk = 'C:\Android\Sdk'; $bt = "$sdk\build-tools\35.0.0"; $java = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:Path = "$java;$env:Path"
New-Item "$root\controls-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\controls-build\compiled" -ItemType Directory -Force | Out-Null
New-Item "$root\controls-build\dex" -ItemType Directory -Force | Out-Null
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$root\controls-build\compiled\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$bt\aapt2.exe" link -o "$root\controls-build\unsigned.apk" -I "$sdk\platforms\android-35\android.jar" --manifest "$root\AndroidManifest.xml" "$root\controls-build\compiled\res.zip" -A "$root\assets" --java "$root\controls-build\generated" --min-sdk-version 24 --target-sdk-version 35 --version-code $version.code --version-name $version.name
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
$sources = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk\platforms\android-35\android.jar" -d "$root\controls-build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& jar cf "$root\controls-build\classes.jar" -C "$root\controls-build\classes" .
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& "$bt\d8.bat" --lib "$sdk\platforms\android-35\android.jar" --output "$root\controls-build\dex" "$root\controls-build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
& jar uf "$root\controls-build\unsigned.apk" -C "$root\controls-build\dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
Copy-Item "$root\controls-build\unsigned.apk" "$root\controls-build\aligned.apk"
& "$bt\zipalign.exe" -f 4 "$root\controls-build\aligned.apk" "$root\controls-build\aligned2.apk"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
$keystore = "$root\signing\debug.keystore"
if (!(Test-Path $keystore)) { & keytool -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000 | Out-Null }
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$root\controls-build\yuejie.apk" "$root\controls-build\aligned2.apk"
if ($LASTEXITCODE -ne 0) { throw 'Android build command failed' }
Write-Output "$root\controls-build\yuejie.apk"
