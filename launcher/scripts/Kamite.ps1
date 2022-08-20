$RootPath = Split-Path $MyInvocation.MyCommand.Path -Parent
$JarPath = Join-Path -Path $RootPath -ChildPath lib\generic\kamite.jar
.\runtime\bin\java.exe --enable-preview -jar $JarPath $args
