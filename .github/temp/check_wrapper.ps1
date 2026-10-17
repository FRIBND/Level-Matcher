$source = "d:\X\ND\Level-Matcher"
$copy = Join-Path $env:TEMP ("lm_copy_" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $copy | Out-Null
Copy-Item "$source\*.py", "$source\Run_Pipeline.ps1" $copy
New-Item -ItemType Directory -Path "$copy\data" | Out-Null
Copy-Item "$source\data\raw" "$copy\data\raw" -Recurse
New-Item -ItemType Directory -Path "$copy\outputs" | Out-Null
Set-Location $copy
"copy folder: $copy"
& "$copy\Run_Pipeline.ps1" -Labels L,Q -Python "C:\Users\sun\AppData\Local\Programs\Python\Python311\python.exe" 2>&1 | Select-Object -Last 22
"--- outputs folder (flat expected) ---"
Get-ChildItem "$copy\outputs" | Select-Object Name, Length, PSIsContainer | Format-Table -AutoSize | Out-String -Width 200
"--- data\json ---"
Get-ChildItem "$copy\data\json" | Select-Object -ExpandProperty Name
Set-Location $source
Remove-Item -Recurse -Force $copy
"copy removed"
