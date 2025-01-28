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
    $lastUpdatedPattern = '(\s*)/\*\* *\r?\n *\* Last Updated: \d{4}-\d{2}-\d{2} *\r?\n *\*/'; ^
    if ($content -match $lastUpdatedPattern) { ^
        Write-Host 'Updating existing timestamp...' -ForegroundColor Yellow; ^
        $indent = $matches[1]; ^
        $newLastUpdatedText = $indent + '/** ' + [Environment]::NewLine + ^
                             $indent + ' * Last Updated: ' + $currentDate + [Environment]::NewLine + ^
                             $indent + ' */'; ^
        $updatedContent = $content -replace $lastUpdatedPattern, $newLastUpdatedText; ^
    } else { ^
        Write-Host 'Adding new timestamp...' -ForegroundColor Yellow; ^
        if ($content -match '(?m)^(\s*)(?=/\*\*?)') { ^
            $indent = $matches[1]; ^
            $newLastUpdatedText = $indent + '/** ' + [Environment]::NewLine + ^
                                 $indent + ' * Last Updated: ' + $currentDate + [Environment]::NewLine + ^
                                 $indent + ' */' + [Environment]::NewLine + [Environment]::NewLine; ^
            $updatedContent = $content -replace '(?s)^(\s*)(\/\*\*?.*?\*\/\r?\n*)', ('$1$2' + $newLastUpdatedText); ^
        } else { ^
            if ($content -match '(?m)^(\s*)\S') { ^
                $baseIndent = $matches[1]; ^
                $indent = $baseIndent + '    '; ^
            } else { ^
                $baseIndent = ''; ^
                $indent = '    '; ^
            }; ^
            $newLastUpdatedText = $indent + '/** ' + [Environment]::NewLine + ^
                                 $indent + ' * Last Updated: ' + $currentDate + [Environment]::NewLine + ^
                                 $indent + ' */' + [Environment]::NewLine + [Environment]::NewLine; ^
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