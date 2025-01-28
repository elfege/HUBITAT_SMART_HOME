#!/usr/bin/env pwsh
$VerbosePreference = 'Continue'

Write-Host 'Starting pre-commit hook for Groovy files...' -ForegroundColor Green
$currentDate = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
Write-Host "Current timestamp: $currentDate"

function Get-FileVersion {
    param (
        [string]$content
    )
    
    # Try to find version in top comment section - now looking for 4 numbers
    if ($content -match '(?s)/\*\*.*?Version:\s*(\d+)\.(\d+)\.(\d+)\.(\d+)\s*.*?\*/') {
        return @{
            Major = [int]$matches[1]  # Immutable
            Minor = [int]$matches[2]
            Build = [int]$matches[3]
            Patch = [int]$matches[4]
        }
    }
    
    Write-Host "No version found, starting at 1.0.0.0"
    return @{
        Major = 1
        Minor = 0
        Build = 0
        Patch = 0
    }
}

function Get-NextVersion {
    param (
        [int]$major,
        [int]$minor,
        [int]$build,
        [int]$patch
    )
    
    $patch += 1
    
    if ($patch > 9) {
        $patch = 0
        $build += 1
        
        if ($build > 9) {
            $build = 0
            $minor += 1
        }
    }
    
    return @{
        Major = $major  # Remains unchanged
        Minor = $minor
        Build = $build
        Patch = $patch
    }
}

# Get all staged Groovy files
$stagedFiles = git diff --name-only --cached | Where-Object { $_ -match '\.groovy$' }
Write-Host "Found staged Groovy files: $($stagedFiles -join ', ')"

foreach ($file in $stagedFiles) {
    Write-Host "Processing: $file" -ForegroundColor Cyan
    
    if (-not (Test-Path $file)) {
        Write-Warning "File not found: $file"
        continue
    }
    
    # Get the modified lines for this file
    $modifiedLines = git diff --cached -U0 $file | 
        Select-String '^\+' | 
        Where-Object { $_ -notmatch '^\+\+\+ ' } | 
        ForEach-Object { $_.ToString().TrimStart('+') }
    
    $content = Get-Content $file -Raw
    if ($null -eq $content) {
        $content = ''
    }
    Write-Host "File content length: $($content.Length) characters"
    
    # Get current version and increment it
    $currentVersion = Get-FileVersion -content $content
    $newVersion = Get-NextVersion -major $currentVersion.Major -minor $currentVersion.Minor `
                                -build $currentVersion.Build -patch $currentVersion.Patch
    
    $currentVersionString = "$($currentVersion.Major).$($currentVersion.Minor).$($currentVersion.Build).$($currentVersion.Patch)"
    $newVersionString = "$($newVersion.Major).$($newVersion.Minor).$($newVersion.Build).$($newVersion.Patch)"
    Write-Host "Incrementing version from $currentVersionString to $newVersionString" -ForegroundColor Yellow
    
    # Update version in file header if it exists
    $updatedContent = $content
    if ($content -match '(?s)(/\*\*.*?Version:\s*)\d+\.\d+\.\d+\.\d+(\s*.*?\*/)') {
        $updatedContent = $content -replace '(?s)(/\*\*.*?Version:\s*)\d+\.\d+\.\d+\.\d+(\s*.*?\*/)', "`${1}$newVersionString`$2"
    }
    
    # Pattern to find function declarations: looks for lines ending with () {
    $functionPattern = '(?m)^(\s*)[^\r\n]*\(\s*\)\s*\{\s*$'
    
    # Pattern to find existing timestamps
    $lastUpdatedPattern = '(\s*)/\*\* *\r?\n *\* Last Updated: \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} *\r?\n *\*/'
    
    # Get all function declarations in the file
    $functionMatches = [regex]::Matches($updatedContent, $functionPattern)
    
    # Process matches in reverse order to maintain string positions
    for ($i = $functionMatches.Count - 1; $i -ge 0; $i--) {
        $match = $functionMatches[$i]
        $functionLine = $updatedContent.Substring($match.Index, $match.Length).Trim()
        
        # Check if this function or its contents were modified
        $functionModified = $false
        $functionEndIndex = $updatedContent.IndexOf('}', $match.Index + $match.Length)
        if ($functionEndIndex -eq -1) {
            $functionEndIndex = $updatedContent.Length
        }
        $functionContent = $updatedContent.Substring($match.Index, $functionEndIndex - $match.Index)
        
        foreach ($modifiedLine in $modifiedLines) {
            if ($functionContent -match [regex]::Escape($modifiedLine)) {
                $functionModified = $true
                break
            }
        }
        
        if ($functionModified) {
            $baseIndent = $match.Groups[1].Value
            $indent = $baseIndent + '    '
            
            # Remove existing timestamp if present
            $nextContent = $updatedContent.Substring($match.Index + $match.Length)
            if ($nextContent -match '^\s*/\*\* *\r?\n *\* Last Updated:') {
                $updatedContent = $updatedContent -replace "$functionLine\s*$lastUpdatedPattern", $functionLine
            }
            
            # Add new timestamp
            $newLastUpdatedText = @"

$indent/** 
$indent * Last Updated: $currentDate
$indent */
"@
            $position = $match.Index + $match.Length
            $updatedContent = $updatedContent.Insert($position, $newLastUpdatedText)
        }
    }
    
    if ($updatedContent -ne $content) {
        $updatedContent | Set-Content $file -NoNewline
        git add $file
        Write-Host "Successfully processed: $file" -ForegroundColor Green
    } else {
        Write-Host "No modifications needed for: $file" -ForegroundColor Yellow
    }
}

Write-Host 'Pre-commit hook completed' -ForegroundColor Green