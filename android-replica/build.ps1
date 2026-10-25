$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = 'C:\Android\Sdk'; $bt = "$sdk\build-tools\35.0.0"; $java = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:Path = "$java;$env:Path"
Remove-Item "$root\build" -Recurse -Force -ErrorAction SilentlyContinue
New-Item "$root\build\classes" -ItemType Directory -Force | Out-Null
New-Item "$root\build\compiled" -ItemType Directory -Force | Out-Null
New-Item "$root\build\dex" -ItemType Directory -Force | Out-Null
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$root\build\compiled\res.zip"
& "$bt\aapt2.exe" link -o "$root\build\unsigned.apk" -I "$sdk\platforms\android-35\android.jar" --manifest "$root\AndroidManifest.xml" "$root\build\compiled\res.zip" -A "$root\assets" --java "$root\build\generated" --min-sdk-version 24 --target-sdk-version 35 --version-code 10 --version-name 0.10
$sources = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath "$sdk\platforms\android-35\android.jar" -d "$root\build\classes" $sources
& "$bt\d8.bat" --lib "$sdk\platforms\android-35\android.jar" --output "$root\build\dex" (Get-ChildItem "$root\build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& jar uf "$root\build\unsigned.apk" -C "$root\build\dex" classes.dex
Copy-Item "$root\build\unsigned.apk" "$root\build\aligned.apk"
& "$bt\zipalign.exe" -f 4 "$root\build\aligned.apk" "$root\build\aligned2.apk"
$keystore = "$root\signing\debug.keystore"
if (!(Test-Path $keystore)) { & keytool -genkeypair -keystore $keystore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -validity 10000 | Out-Null }
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out "$root\build\yuejie.apk" "$root\build\aligned2.apk"
Write-Output "$root\build\yuejie.apk"
