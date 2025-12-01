#!/bin/bash
# pre-commit-groovy.sh

# Path to the centralized scripts directory
SCRIPTS_DIR="H:/OneDrive/Documents/PROJECTS/HUBITAT"

# Path to the PowerShell script
PS_SCRIPT_PATH="$SCRIPTS_DIR/Update-Timestamp.ps1"

# Check if the PowerShell script exists
if [ ! -f "$PS_SCRIPT_PATH" ]; then
    echo "[Error] PowerShell script not found at '$PS_SCRIPT_PATH'"
    exit 1
fi

# Get list of staged .groovy files
groovyFiles=$(git diff --cached --name-only --diff-filter=ACM | grep -i "\.groovy$")

for file in $groovyFiles; do
    # Call the PowerShell script to update the timestamp
    pwsh -NoProfile -ExecutionPolicy Bypass -File "$PS_SCRIPT_PATH" "$file"
    
    # Check if PowerShell script succeeded
    if [ $? -ne 0 ]; then
        echo "[Error] Failed to update timestamp for '$file'"
        exit 1
    fi
    
    # Add the modified file back to the staging area
    git add "$file"
done

exit 0
