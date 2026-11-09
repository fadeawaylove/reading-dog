$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$true
$root=$PSScriptRoot
if(Test-Path "$root/build/classes"){
 $classesPath=(Resolve-Path -LiteralPath "$root/build/classes").Path
 if(!$classesPath.StartsWith((Resolve-Path -LiteralPath $root).Path+[IO.Path]::DirectorySeparatorChar)){throw 'Build output escaped backend directory'}
 Remove-Item -LiteralPath $classesPath -Recurse -Force
}
New-Item -ItemType Directory -Force "$root/build/classes","$root/build/lib" | Out-Null
$deps=@{
 'json.jar'='org/json/json/20240303/json-20240303.jar'
 'sqlite.jar'='org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar'
 'jsoup.jar'='org/jsoup/jsoup/1.17.2/jsoup-1.17.2.jar'
 'slf4j-api.jar'='org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar'
 'slf4j-nop.jar'='org/slf4j/slf4j-nop/2.0.13/slf4j-nop-2.0.13.jar'
}
foreach($d in $deps.GetEnumerator()){if(!(Test-Path "$root/build/lib/$($d.Key)")){Invoke-WebRequest "https://repo.maven.apache.org/maven2/$($d.Value)" -OutFile "$root/build/lib/$($d.Key)"}}
$sources=Get-ChildItem "$root/src" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 --release 17 -cp "$root/build/lib/*" -d "$root/build/classes" $sources
& jar --create --file "$root/build/server.jar" -C "$root/build/classes" .
New-Item -ItemType Directory -Force "$root/build/native" | Out-Null
$zip=[IO.Compression.ZipFile]::OpenRead("$root/build/lib/sqlite.jar")
try{[IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('org/sqlite/native/Linux/x86_64/libsqlitejdbc.so'),"$root/build/native/libsqlitejdbc.so",$true)}finally{$zip.Dispose()}
