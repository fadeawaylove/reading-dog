$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = Get-Content (Join-Path $root 'version.json') -Raw | ConvertFrom-Json
$sdk = 'C:\Android\Sdk'; $bt = "$sdk\build-tools\35.0.0"; $java = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:Path = "$java;$env:Path"
Remove-Item "$root\touch-build" -Recurse -Force -ErrorAction SilentlyContinue
New-Item "$root\touch-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\touch-build\compiled" -ItemType Directory -Force | Out-Null
New-Item "$root\touch-build\dex" -ItemType Directory -Force | Out-Null
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$root\touch-build\compiled\res.zip"
& "$bt\aapt2.exe" link -o "$root\touch-build\unsigned.apk" -I "$sdk\platforms\android-35\android.jar" --manifest "$root\AndroidManifest.xml" "$root\touch-build\compiled\res.zip" -A "$root\assets" --java "$root\touch-build\generated" --min-sdk-version 24 --target-sdk-version 35 --version-code $version.code --version-name $version.name
$sources = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk\platforms\android-35\android.jar" -d "$root\touch-build\classes" $sources
& jar cf "$root\touch-build\classes.jar" -C "$root\touch-build\classes" .
& "$bt\d8.bat" --lib "$sdk\platforms\android-35\android.jar" --output "$root\touch-build\dex" "$root\touch-build\classes.jar"
& jar uf "$root\touch-build\unsigned.apk" -C "$root\touch-build\dex" classes.dex
Copy-Item "$root\touch-build\unsigned.apk" "$root\touch-build\aligned.apk"
& "$bt\zipalign.exe" -f 4 "$root\touch-build\aligned.apk" "$root\touch-build\aligned2.apk"
$keystore = "$root\signing\debug.keystore"
if (!(Test-Path $keystore)) { & keytool -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000 | Out-Null }
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$root\touch-build\yuejie.apk" "$root\touch-build\aligned2.apk"
Write-Output "$root\touch-build\yuejie.apk"
