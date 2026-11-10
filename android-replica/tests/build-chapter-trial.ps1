$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=$PSScriptRoot
$app=Split-Path -Parent $root
$out="$root/chapter-trial-build"
$bt='C:/Android/Sdk/build-tools/35.0.0'
$android='C:/Android/Sdk/platforms/android-35/android.jar'
New-Item -ItemType Directory -Force "$out/classes","$out/dex","$out/assets" | Out-Null
# Never include the private backend token in the isolated test APK.
Get-ChildItem "$app/assets" -File | Where-Object Name -ne 'backend-private.json' | Copy-Item -Destination "$out/assets"
$sources=Get-ChildItem "$app/src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -cp $android -d "$out/classes" "$root/ChapterTrialChecks.java" $sources
& "$bt/aapt2.exe" link -o "$out/unsigned.apk" -I $android --manifest "$root/ChapterTrialManifest.xml" -A "$out/assets" --min-sdk-version 24 --target-sdk-version 28
& jar cf "$out/checks.jar" -C "$out/classes" .
& "$bt/d8.bat" --lib $android --output "$out/dex" "$out/checks.jar"
& jar uf "$out/unsigned.apk" -C "$out/dex" classes.dex
& "$bt/zipalign.exe" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
& "$bt/apksigner.bat" sign --ks "$app/signing/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/checks.apk" "$out/aligned.apk"
Write-Output "$out/checks.apk"
