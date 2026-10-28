$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
python "$root\prepare-typography-assets.py"
if($LASTEXITCODE -ne 0){throw 'Reference assets preparation failed'}
$out=Join-Path $root 'linebreak-build'
$bt='C:\Android\Sdk\build-tools\35.0.0';$jar='C:\Android\Sdk\platforms\android-35\android.jar';$java='C:\Program Files\Android\Android Studio\jbr\bin'
New-Item "$out\classes","$out\dex" -ItemType Directory -Force | Out-Null
$sources=Get-ChildItem "$root\..\src" -Recurse -Filter *.java | ForEach-Object FullName
& "$java\javac.exe" -encoding UTF-8 -source 8 -target 8 -cp $jar -d "$out\classes" $sources "$root\TypographyChecks.java" "$root\LineBreakChecks.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed'}
& "$bt\aapt2.exe" link -o "$out\unsigned.apk" -I $jar --manifest "$root\LineBreakManifest.xml" -A "$root\typography-assets" --min-sdk-version 24 --target-sdk-version 35
& "$java\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$bt\d8.bat" --lib $jar --output "$out\dex" "$out\classes.jar"
& "$java\jar.exe" uf "$out\unsigned.apk" -C "$out\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"
& "$bt\apksigner.bat" sign --ks "$root\..\signing\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out\checks.apk" "$out\aligned.apk"
