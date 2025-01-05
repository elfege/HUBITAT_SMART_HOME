# Groovy Last Updated Git Hook

A Git pre-commit hook that automatically manages "Last Updated" timestamps in Groovy source files. The hook runs before each commit, scanning staged Groovy files and either updating existing timestamps or adding new ones.

## Features

- Automatically processes all staged `.groovy` files during commit
- Updates existing "Last Updated" timestamps to the current date
- Adds new timestamp blocks for files that don't have one
- Maintains proper formatting around comment blocks
- Preserves existing file headers
- Cleans up duplicate timestamp entries

## Installation

1. Save the script as `pre-commit-groovy.cmd` in your repository's `.git/hooks` directory
2. Create a file named `pre-commit` (no extension) in the same directory with this content:

   ```
   @%~dp0pre-commit-groovy.cmd
   ```

## Requirements

- Windows environment
- PowerShell
- Git

## How It Works

When you make a commit, the hook:

1. Identifies all staged `.groovy` files
2. For each file:
   - Checks for existing "Last Updated" entries
   - Removes any duplicate timestamp blocks
   - Updates or adds a timestamp in this format:

     ```groovy
     /**
      * Last Updated: YYYY-MM-DD
      */
     ```

3. Places the timestamp block:
   - After the main file header if one exists
   - At the start of the file if no header exists
4. Automatically stages the modified files

## Example

Original file:

```groovy
/**
 * My Groovy Application
 * Version: 1.0
 */
def myFunction() {
    // code here
}
```

After commit:

```groovy
/**
 * My Groovy Application
 * Version: 1.0
 */
/** 
 * Last Updated: 2025-01-02
 */
def myFunction() {
    // code here
}
```

## Notes

- The hook maintains a clean file format with appropriate spacing
- It preserves all other content in the file
- Timestamps use UTC date in YYYY-MM-DD format
- The hook only processes files that are staged for commit
