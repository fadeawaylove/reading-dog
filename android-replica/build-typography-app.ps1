$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = Get-Content (Join-Path $root 'version.json') -Raw | ConvertFrom-Json
$sdk = 'C:\Android\Sdk'; $bt = "$sdk\build-tools\35.0.0"; $java = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:Path = "$java;$env:Path"

New-Item "$root\typography-app-build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\typography-app-build\compiled" -ItemType Directory -Force | Out-Null
New-Item "$root\typography-app-build\dex" -ItemType Directory -Force | Out-Null
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$root\typography-app-build\compiled\res.zip"
& "$bt\aapt2.exe" link -o "$root\typography-app-build\unsigned.apk" -I "$sdk\platforms\android-35\android.jar" --manifest "$root\AndroidManifest.xml" "$root\typography-app-build\compiled\res.zip" -A "$root\assets" --java "$root\typography-app-build\generated" --min-sdk-version 24 --target-sdk-version 35 --version-code $version.code --version-name $version.name
$sources = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk\platforms\android-35\android.jar" -d "$root\typography-app-build\classes" $sources
& jar cf "$root\typography-app-build\classes.jar" -C "$root\typography-app-build\classes" .
& "$bt\d8.bat" --lib "$sdk\platforms\android-35\android.jar" --output "$root\typography-app-build\dex" "$root\typography-app-build\classes.jar"
& jar uf "$root\typography-app-build\unsigned.apk" -C "$root\typography-app-build\dex" classes.dex
Copy-Item "$root\typography-app-build\unsigned.apk" "$root\typography-app-build\aligned.apk"
& "$bt\zipalign.exe" -f 4 "$root\typography-app-build\aligned.apk" "$root\typography-app-build\aligned2.apk"
$keystore = "$root\signing\debug.keystore"
if (!(Test-Path $keystore)) { & keytool -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000 | Out-Null }
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$root\typography-app-build\yuejie.apk" "$root\typography-app-build\aligned2.apk"
Write-Output "$root\typography-app-build\yuejie.apk"
