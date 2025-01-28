#!/usr/bin/env pwsh
$VerbosePreference = 'Continue'

Write-Host 'Starting pre-commit hook for Groovy files...' -ForegroundColor Green
$currentDate = Get-Date -Format 'yyyy-MM-dd'
Write-Host "Current date: $currentDate"

# Get all staged Groovy files
$stagedFiles = git diff --name-only --cached | Where-Object { $_ -match '\.groovy$' }
Write-Host "Found staged Groovy files: $($stagedFiles -join ', ')"

foreach ($file in $stagedFiles) {
    Write-Host "Processing: $file" -ForegroundColor Cyan
    
    if (-not (Test-Path $file)) {
        Write-Warning "File not found: $file"
        continue
    }
    
    $content = Get-Content $file -Raw
    if ($null -eq $content) {
        $content = ''
    }
    Write-Host "File content length: $($content.Length) characters"
    
    # Pattern to find function declarations: looks for lines ending with () {
    $functionPattern = '(?m)^(\s*)[^\r\n]*\(\s*\)\s*\{\s*$'
    
    # Pattern to find existing timestamps
    $lastUpdatedPattern = '(\s*)/\*\* *\r?\n *\* Last Updated: \d{4}-\d{2}-\d{2} *\r?\n *\*/'
    
    # Update existing timestamps or add new ones after functions
    if ($content -match $lastUpdatedPattern) {
        Write-Host 'Updating existing timestamp...' -ForegroundColor Yellow
        $indent = $matches[1]
        $newLastUpdatedText = @"
$indent/** 
$indent * Last Updated: $currentDate
$indent */
"@
        $updatedContent = $content -replace $lastUpdatedPattern, $newLastUpdatedText
    }
    else {
        Write-Host 'Adding new timestamp after function declarations...' -ForegroundColor Yellow
        $updatedContent = $content
        
        # Find all function declarations
        $matches = [regex]::Matches($content, $functionPattern)
        
        # Process matches in reverse order to maintain string positions
        for ($i = $matches.Count - 1; $i -ge 0; $i--) {
            $match = $matches[$i]
            $baseIndent = $match.Groups[1].Value
            $indent = $baseIndent + '    '
            
            # Check if there's already a timestamp after this function
            $nextContent = $content.Substring($match.Index + $match.Length)
            if (-not ($nextContent -match '^\s*/\*\* *\r?\n *\* Last Updated:')) {
                $newLastUpdatedText = @"

$indent/** 
$indent * Last Updated: $currentDate
$indent */
"@
                $position = $match.Index + $match.Length
                $updatedContent = $updatedContent.Insert($position, $newLastUpdatedText)
            }
        }
    }
    
    $updatedContent | Set-Content $file -NoNewline
    git add $file
    Write-Host "Successfully processed: $file" -ForegroundColor Green
}

Write-Host 'Pre-commit hook completed' -ForegroundColor Green