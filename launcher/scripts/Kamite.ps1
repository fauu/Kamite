$RootPath = Split-Path $MyInvocation.MyCommand.Path -Parent
$JarPath = Join-Path -Path $RootPath -ChildPath lib\generic\kamite.jar
java --enable-preview -jar $JarPath $args