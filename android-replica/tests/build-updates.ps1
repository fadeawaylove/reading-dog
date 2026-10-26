$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$app=Split-Path -Parent $root
$out=Join-Path $root 'updates-build'
$bt='C:\Android\Sdk\build-tools\35.0.0'
$android='C:\Android\Sdk\platforms\android-35\android.jar'
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$env:Path="$java;$env:Path"
New-Item -ItemType Directory -Force "$out/classes","$out/dex","$out/assets" | Out-Null
$version=Get-Content "$app/version.json" -Raw | ConvertFrom-Json
$futureVersion="$($version.name).1"
[IO.File]::WriteAllText("$out/assets/future-version.txt",$futureVersion)
# A future-version fixture only: never published or installed by these tests.
& "$bt/aapt2.exe" link -o "$out/future-unsigned.apk" -I $android --manifest "$app/AndroidManifest.xml" "$app/build/compiled/res.zip" -A "$app/assets" --min-sdk-version 24 --target-sdk-version 35 --version-code ($version.code+1) --version-name $futureVersion
& "$java/jar.exe" uf "$out/future-unsigned.apk" -C "$app/build/dex" classes.dex
& "$bt/zipalign.exe" -f 4 "$out/future-unsigned.apk" "$out/future-aligned.apk"
& "$bt/apksigner.bat" sign --ks "$app/signing/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/assets/future.apk" "$out/future-aligned.apk"
if(!(Test-Path "$out/fixture.keystore")){& "$java/keytool.exe" -genkeypair -keystore "$out/fixture.keystore" -storepass android -alias fixture -keypass android -dname 'CN=Update Test Fixture' -keyalg RSA -validity 30}
& "$bt/apksigner.bat" sign --ks "$out/fixture.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/assets/wrong-signer.apk" "$out/future-aligned.apk"
& "$java/javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$android;$app/build/classes" -d "$out/classes" "$root/UpdateChecks.java"
& "$bt/aapt2.exe" link -o "$out/unsigned.apk" -I $android --manifest "$root/UpdatesManifest.xml" -A "$out/assets" --min-sdk-version 24 --target-sdk-version 28
& "$java/jar.exe" cf "$out/classes.jar" -C "$out/classes" .
& "$bt/d8.bat" --lib $android --classpath "$app/build/classes.jar" --output "$out/dex" "$out/classes.jar"
& "$java/jar.exe" uf "$out/unsigned.apk" -C "$out/dex" classes.dex
& "$bt/zipalign.exe" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
& "$bt/apksigner.bat" sign --ks "$app/signing/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/checks.apk" "$out/aligned.apk"
Write-Output "$out/checks.apk"
