# H:\OneDrive\Documents\PROJECTS\HUBITAT\Update-Timestamp.ps1

Param (
    [string]$filePath
)

# Read all lines from the file
$content = Get-Content -Path $filePath

# Define the timestamp pattern
$timestampPattern = '^\s*\*\s+Last Updated:\s+\d{4}-\d{2}-\d{2}'

# Get today's date in desired format
$today = Get-Date -Format 'yyyy-MM-dd'

# Initialize a flag to check if timestamp exists
$timestampFound = $false

# Iterate through each line to find and update the timestamp
for ($i = 0; $i -lt $content.Length; $i++) {
    if ($content[$i] -match $timestampPattern) {
        $content[$i] = " * Last Updated: $today"
        $timestampFound = $true
        break
    }
}

# If timestamp not found, insert it after the opening comment
if (-not $timestampFound) {
    # Find the line with '/**'
    $startIndex = $content.IndexOf('/**')
    if ($startIndex -ne -1) {
        # Insert the timestamp line after '/**'
        $content = $content[0..$startIndex] + " * Last Updated: $today" + $content[($startIndex + 1)..($content.Length - 1)]
    } else {
        # If no header comment exists, create one
        $content = "/**`n * Last Updated: $today`n */`n" + $content
    }
}

# Remove any duplicate timestamp blocks
# This assumes that the header is within the first 10 lines
$headerEnd = $content.IndexOf('*/') + 1
$header = $content[0..$headerEnd]
$rest = $content[($headerEnd + 1)..($content.Length - 1)]

# Remove duplicate timestamp lines in the header
$header = $header | Select-Object -Unique

# Combine header and rest of the file
$newContent = $header + $rest

# Write the updated content back to the file
Set-Content -Path $filePath -Value $newContent -Encoding UTF8
