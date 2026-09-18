# =============================================================================
# Run_Pipeline.ps1 — Full Codebase Workflow Wrapper
# =============================================================================
#
# High-level Structure and Workflow:
#
# Purpose: Execute the complete Level-Matcher pipeline in sequence without
#          freezing VS Code. Level_Matcher.py is spawned as a detached external
#          process with its stdout redirected to a log file, allowing the
#          terminal to remain responsive during heavy ML computation.
#
# Workflow:
#
#   [1. Dataset Parser]          Runs synchronously (lightweight, <5s)
#          |
#          v
#   [2. Level Matcher]           Spawned as detached process (heavy ML compute)
#          | -- logs to --> outputs/run_log.txt (streamed live in terminal)
#          v
#   [3. Combined Visualizer]     Runs synchronously after Level Matcher exits
#          |
#          v
#   [outputs/figures/*.png]      Final visual results
#
# Usage:
#   Open a PowerShell terminal in the project root, then run:
#       .\Run_Pipeline.ps1
#
# =============================================================================

$python = "C:\Users\sun\AppData\Local\Programs\Python\Python311\python.exe"
$project_dir = $PSScriptRoot
$log_file = Join-Path $project_dir "outputs\run_log.txt"

Set-Location $project_dir

# -----------------------------------------------------------------------
# Step 1: Dataset Parser (synchronous — fast and safe in VS Code terminal)
# -----------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host " Step 1/3: Dataset Parser" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
& $python "Dataset_Parser.py"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Dataset_Parser.py failed with exit code $LASTEXITCODE. Aborting." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Dataset Parser complete." -ForegroundColor Green

# -----------------------------------------------------------------------
# Step 2: Level Matcher (detached process — heavy ML compute)
#         Spawned outside VS Code to prevent terminal freeze.
#         stdout is redirected to outputs/run_log.txt.
#         This script streams the log file live so you can monitor progress.
# -----------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host " Step 2/3: Level Matcher (spawning detached process)" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# Clear previous logs
$error_log_file = Join-Path $project_dir "outputs\run_error_log.txt"
if (Test-Path $log_file) { Remove-Item $log_file -Force }
if (Test-Path $error_log_file) { Remove-Item $error_log_file -Force }

$process_arguments = @{
    FilePath               = $python
    ArgumentList           = "Level_Matcher.py"
    WorkingDirectory       = $project_dir
    RedirectStandardOutput = $log_file
    RedirectStandardError  = $error_log_file
    NoNewWindow            = $true
    PassThru               = $true
}
$spawned_process = Start-Process @process_arguments

Write-Host "[INFO] Level Matcher PID: $($spawned_process.Id)" -ForegroundColor Yellow
Write-Host "[INFO] Streaming log from: $log_file"
Write-Host "[INFO] Press Ctrl+C once to stop streaming (process continues in background)"
Write-Host ""

# Stream log live until Level Matcher finishes
$previous_line_count = 0
while (-not $spawned_process.HasExited) {
    Start-Sleep -Milliseconds 500
    if (Test-Path $log_file) {
        $lines = Get-Content $log_file
        if ($lines.Count -gt $previous_line_count) {
            $lines[$previous_line_count..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
            $previous_line_count = $lines.Count
        }
    }
}

# Print any remaining lines after process exits
if (Test-Path $log_file) {
    $lines = Get-Content $log_file
    if ($lines.Count -gt $previous_line_count) {
        $lines[$previous_line_count..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
    }
}

# Failure detection: the Process object returned by Start-Process does not expose its
# ExitCode on this system (verified with a trivial test process), so failures are
# detected from the logs instead. A Python crash writes a traceback to the stderr log
# (mirrored in the stdout log), while a healthy run leaves both logs traceback-free.
$log_has_traceback = (Test-Path $log_file) -and (Select-String -Path $log_file -Pattern "Traceback" -Quiet)
$stderr_has_traceback = (Test-Path $error_log_file) -and ((Get-Item $error_log_file).Length -gt 0) -and (Select-String -Path $error_log_file -Pattern "Traceback" -Quiet)
if ($log_has_traceback -or $stderr_has_traceback) {
    Write-Host ""
    Write-Host "[ERROR] Level_Matcher.py reported an exception. Check logs:" -ForegroundColor Red
    Write-Host "  stdout: $log_file" -ForegroundColor Red
    Write-Host "  stderr: $error_log_file" -ForegroundColor Red
    if (Test-Path $error_log_file) {
        Write-Host ""
        Write-Host "--- stderr output ---" -ForegroundColor Red
        Get-Content $error_log_file | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    }
    exit 1
}
Write-Host ""
Write-Host "[OK] Level Matcher complete." -ForegroundColor Green

# -----------------------------------------------------------------------
# Step 3: Combined Visualizer (synchronous — lightweight rendering)
# -----------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host " Step 3/3: Combined Visualizer" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
& $python "Combined_Visualizer.py"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Combined_Visualizer.py failed with exit code $LASTEXITCODE." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Visualizer complete." -ForegroundColor Green

# -----------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host " Pipeline complete. Output files:" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
Get-ChildItem "outputs" -Recurse -File | Select-Object -Property @{Name="File"; Expression={$_.FullName.Replace($project_dir + "\", "")}}, LastWriteTime | Format-Table -AutoSize
