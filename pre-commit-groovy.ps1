# pre-commit.ps1



#!/usr/bin/env pwsh
$VerbosePreference = 'Continue'

logger 'Starting pre-commit hook for Groovy files...' -ForegroundColor Green
$currentDate = [DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss')
logger "Current timestamp: $currentDate"  

function Get-FunctionLastModified {
    param (
        [string]$filePath,
        [int]$startLine,
        [int]$endLine
    )
    
    try {
        $lineRange = "$startLine,${endLine}"
        $gitLog = git log -L "$lineRange:$filePath" --format="%ai" 2>&1
        $logMatch = [regex]::Match($gitLog, '(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})')
        if ($logMatch.Success) {
            return [DateTime]::Parse($logMatch.Value).ToString('yyyy-MM-dd HH:mm:ss')
        }
    }
    catch {
        Write-Warning "Could not get git history for function at line $startLine"
    }
    
    return $currentDate
}

function Get-FileVersion {
    param (
        [string]$content
    )
    # Use a more robust pattern to capture the version anywhere inside the /** ... */
    if ($content -match '(?s)/\*\*[\s\S]*?Version:\s*(\d+)\.(\d+)\.(\d+)\.(\d+)[\s\S]*?\*/') {
        return @{
            Major = [int]$matches[1]  # Must remain immutable
            Minor = [int]$matches[2]
            Build = [int]$matches[3]
            Patch = [int]$matches[4]
        }
    }

    logger "No version found, starting at 1.0.0.0"
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
    if ($patch -gt 9) {
        $patch = 0
        $build += 1
        
        if ($build -gt 9) {
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
logger "Found staged Groovy files: $($stagedFiles -join ', ')"

foreach ($file in $stagedFiles) {
    logger "Processing: $file" -ForegroundColor Cyan
    
    if (-not (Test-Path $file)) {
        Write-Warning "File not found: $file"
        continue
    }
    
    $modifiedLines = git diff --cached -U0 $file |
        Select-String '^\+' |
        Where-Object { $_ -notmatch '^\+\+\+ ' } |
        ForEach-Object { $_.ToString().TrimStart('+') }
    
    $content = Get-Content $file -Raw
    if ($null -eq $content) {
        $content = ''
    }
    logger "File content length: $($content.Length) characters"
    
    # Get current version and compute next version
    $currentVersion = Get-FileVersion -content $content
    $newVersion = Get-NextVersion -major $currentVersion.Major `
                                  -minor $currentVersion.Minor `
                                  -build $currentVersion.Build `
                                  -patch $currentVersion.Patch
    
    $currentVersionString = "$($currentVersion.Major).$($currentVersion.Minor).$($currentVersion.Build).$($currentVersion.Patch)"
    $newVersionString = "$($newVersion.Major).$($newVersion.Minor).$($newVersion.Build).$($newVersion.Patch)"
    logger "Incrementing version from $currentVersionString to $newVersionString" -ForegroundColor Yellow
    
    # Update the version block if it exists
    $updatedContent = $content
    $versionPattern = '(?s)(/\*\*[\s\S]*?Version:\s*)\d+\.\d+\.\d+\.\d+([\s\S]*?\*/)'
    if ($updatedContent -match $versionPattern) {
        $updatedContent = $updatedContent -replace $versionPattern, "`${1}$newVersionString`$2"
    }
    
    # Only match lines ending in ") {"
    $functionPattern = '(?m)^(\s*)[^\r\n]*?\)\s*\{\s*$'
    $lastUpdatedPattern = '(?s)(\s*)/\*\* *\r?\n *\* Last Updated: \d{4}-\d{2}-\d{2}(?: \d{2}:\d{2}:\d{2})? *\r?\n *\*/'
    $functionMatches = [regex]::Matches($updatedContent, $functionPattern)
    
    for ($i = $functionMatches.Count - 1; $i -ge 0; $i--) {
        $match = $functionMatches[$i]
        $functionLine = $updatedContent.Substring($match.Index, $match.Length).Trim()
        
        # Determine function boundaries
        $lineNumber = ($updatedContent.Substring(0, $match.Index) -split "`n").Length
        $functionEndIndex = $match.Index + $match.Length
        $braceCount = 1
        while ($braceCount -gt 0 -and $functionEndIndex -lt $updatedContent.Length) {
            $char = $updatedContent[$functionEndIndex]
            if ($char -eq '{') { $braceCount++ }
            elseif ($char -eq '}') { $braceCount-- }
            $functionEndIndex++
        }
        $endLineNumber = ($updatedContent.Substring(0, $functionEndIndex) -split "`n").Length
        
        $functionContent = $updatedContent.Substring($match.Index, $functionEndIndex - $match.Index)
        $functionModified = $false
        foreach ($modifiedLine in $modifiedLines) {
            if ($functionContent -match [regex]::Escape($modifiedLine)) {
                $functionModified = $true
                break
            }
        }
        
        $baseIndent = $match.Groups[1].Value
        $indent = $baseIndent + '    '
        
        $nextContent = $updatedContent.Substring($match.Index + $match.Length)
        $hasTimestamp = $nextContent -match '^\s*/\*\* *\r?\n *\* Last Updated:'
        
        logger "Function at line $lineNumber - Modified: $functionModified, Has Timestamp: $hasTimestamp"
        
        if ($functionModified -or -not $hasTimestamp) {
            if ($hasTimestamp) {
                $updatedContent = $updatedContent -replace "$functionLine\s*$lastUpdatedPattern", $functionLine
            }
            
            $timestamp = if ($functionModified) {
                $currentDate
            } else {
                $lastModified = Get-FunctionLastModified -filePath $file -startLine $lineNumber -endLine $endLineNumber
                [DateTime]::Parse($lastModified).ToString('yyyy-MM-dd HH:mm:ss')
            }
            
            $timestampBlock = @"
$indent/**
$indent * Last Updated: $timestamp
$indent */
"@
            
            logger "Adding timestamp: $timestamp"
            $position = $match.Index + $match.Length
            $updatedContent = $updatedContent.Insert($position, $timestampBlock)
        }
    }
    
    if ($updatedContent -ne $content) {
        $updatedContent | Set-Content $file -NoNewline
        git add $file
        logger "Successfully processed: $file" -ForegroundColor Green
    }
    else {
        logger "No modifications needed for: $file" -ForegroundColor Yellow
    }
}

logger 'Pre-commit hook completed' -ForegroundColor Green
