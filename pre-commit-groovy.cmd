@echo off
:: Git Pre-commit Hook for Groovy Files
SETLOCAL EnableDelayedExpansion

echo ====== Git Pre-commit Hook Started ======

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
"$VerbosePreference = 'Continue'; ^
Write-Host 'Starting pre-commit hook for Groovy files...' -ForegroundColor Green; ^
$currentDate = Get-Date -Format 'yyyy-MM-dd'; ^
Write-Host ('Current date: ' + $currentDate); ^
$stagedFiles = git diff --name-only --cached ^| Where-Object { $_ -match '\.groovy$' }; ^
Write-Host ('Found staged Groovy files: ' + ($stagedFiles -join ', ')); ^
foreach ($file in $stagedFiles) { ^
    Write-Host ('Processing: ' + $file) -ForegroundColor Cyan; ^
    if (-not (Test-Path $file)) { ^
        Write-Warning ('File not found: ' + $file); ^
        continue; ^
    }; ^
    $content = Get-Content $file -Raw; ^
    if ($null -eq $content) { ^
        $content = ''; ^
    }; ^
    Write-Host ('File content length: ' + $content.Length + ' characters'); ^
    $lastUpdatedPattern = ' \* Last Updated: \d{4}-\d{2}-\d{2}'; ^
    $newLastUpdatedText = ' /** ' + [Environment]::NewLine + ' * Last Updated: ' + $currentDate + [Environment]::NewLine + ' */' + [Environment]::NewLine + [Environment]::NewLine; ^
    if ($content -match $lastUpdatedPattern) { ^
        Write-Host 'Updating existing timestamp...' -ForegroundColor Yellow; ^
        $updatedContent = $content -replace $lastUpdatedPattern, ('* Last Updated: ' + $currentDate); ^
    } else { ^
        Write-Host 'Adding new timestamp...' -ForegroundColor Yellow; ^
        if ($content -match '(?s)^/\*\*?.*?\*/') { ^
            $updatedContent = $content -replace '(?s)^(/\*\*?.*?\*/\r?\n*)', ('$1' + $newLastUpdatedText); ^
        } else { ^
            $updatedContent = $newLastUpdatedText + $content; ^
        } ^
    }; ^
    $updatedContent ^| Set-Content $file -NoNewline; ^
    git add $file; ^
    Write-Host ('Successfully processed: ' + $file) -ForegroundColor Green; ^
}; ^
Write-Host 'Pre-commit hook completed' -ForegroundColor Green;"

echo ====== Git Pre-commit Hook Completed ======
exit /b 0