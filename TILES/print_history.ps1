# File history logging script
$outputFile = "file_history.txt"

# Array of files to track
$files = @(
    "index.html",
    "tiles.js", 
    "tiles.css",
    "roundslider.js",
    "roundslider.css",
    "README.md"
)

# Clear or create the output file
"" | Set-Content $outputFile

# Loop through each file and log its history
foreach ($file in $files) {
    # Add header for the file
    "=== $file History ===" | Add-Content $outputFile
    
    # Get git log for the file and append to output
    $gitLog = git log --follow --date=format:"%Y-%m-%d %H:%M:%S" --pretty=format:"%h - %ad - %s" -- $file
    if ($gitLog) {
        $gitLog | Add-Content $outputFile
    } else {
        "No history found" | Add-Content $outputFile
    }
    
    # Add blank line between files
    "" | Add-Content $outputFile
}

Write-Host "File history has been written to $outputFile"