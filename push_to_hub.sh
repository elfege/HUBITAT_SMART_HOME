#!/bin/bash
# push_to_hub.sh
# Pushes updated Groovy code to Hubitat hubs via HTTP API
# Reads metadata.json to map files to hub IDs

clear

. ~/.env.colors

# PID-based locking to prevent duplicate instances
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
SCRIPT_NAME_NO_EXT="${SCRIPT_NAME%.*}"
export MAIN_PID=$$
PID_FILE="/tmp/${SCRIPT_NAME_NO_EXT}.pid"
touch "$PID_FILE"

PREVIOUS_PID="$(cat "$PID_FILE")"

# Single-instance guard: check if a previous instance is still running
if [ -n "$PREVIOUS_PID" ]; then
    if kill -0 "${PREVIOUS_PID}" 2>/dev/null; then
        echo -e "$ERROR $0 already running (PID: $PREVIOUS_PID)."
        exit 0
    fi
fi

echo "$MAIN_PID" >"$PID_FILE"

# Hub IP addresses (matches backup_hubitat.sh)
declare -A HUB_IPS=(
    ["SMARTHOME1"]="192.168.10.69"
    ["SMARTHOME2"]="192.168.10.70"
    ["SMARTHOME3"]="192.168.10.71"
    ["SMARTHOME4"]="192.168.10.72"
)

HUBITAT_BASE="/home/elfege/HUBITAT"
SYNC_SCRIPT="/home/elfege/0_SCRIPTS/0_SYNC/sync_HUBITAT.sh"

cleanup() {
    : >"$PID_FILE"
}

trap cleanup EXIT

show_menu() {
    cat <<EOF

${BG_BLUE}═══════════════════════════════════════════════════════${NC}
${BG_BLUE}  Hubitat Code Push Utility  ${NC}
${BG_BLUE}═══════════════════════════════════════════════════════${NC}

Select operation:
  1. Push DRIVER to all hubs (sync + update)
  2. Push APP to all hubs (sync + update)
  3. Push ANY file to all hubs (sync + update)
  4. Show help/information
  5. Exit

Automatically:
  - Runs bidirectional sync via ~/0_SCRIPTS/0_SYNC/sync_HUBITAT.sh
  - Reads per-instance metadata.json to find hub IDs
  - POSTs code to each hub's driver/app endpoint
  - Increments version number
  - Detects new vs existing drivers/apps
  - Handles duplicate entries with user selection

EOF
}

# Interactive menu system
show_menu

read -p "Enter selection (1-5): " selection

case "$selection" in
    1)
        echo -e "$INFO Selected: Push DRIVER"
        echo ""
        echo "Available drivers:"

        # Find all .groovy files in DRIVERS directory (exclude deprecated, copies, and old versions)
        mapfile -t driver_files < <(find "${HUBITAT_BASE}/SMARTHOME_MAIN/DRIVERS" -type f -name "*.groovy" \
            -not -ipath "*deprecated*" \
            -not -name "* copy.groovy" \
            -not -iname "*_OLD_*" \
            -not -iname "*old*" \
            -printf "%P\n" | sort)

        if [[ ${#driver_files[@]} -eq 0 ]]; then
            echo -e "$ERROR No driver files found"
            exit 1
        fi

        # Display numbered list (basename only)
        for i in "${!driver_files[@]}"; do
            echo "  $((i + 1)). $(basename "${driver_files[$i]}")"
        done

        echo ""
        read -p "Select driver number (1-${#driver_files[@]}): " driver_selection

        if [[ ! "$driver_selection" =~ ^[0-9]+$ ]] || [[ "$driver_selection" -lt 1 ]] || [[ "$driver_selection" -gt ${#driver_files[@]} ]]; then
            echo -e "$ERROR Invalid selection"
            exit 1
        fi

        FILE_PATH="DRIVERS/${driver_files[$((driver_selection - 1))]}"
        echo -e "$INFO Selected: $FILE_PATH"
        ;;
    2)
        echo -e "$INFO Selected: Push APP"
        echo ""
        echo "Available apps:"

        # Find all .groovy files in APPS directory (exclude deprecated, copies, and old versions)
        mapfile -t app_files < <(find "${HUBITAT_BASE}/SMARTHOME_MAIN/APPS" -type f -name "*.groovy" \
            -not -ipath "*deprecated*" \
            -not -name "* copy.groovy" \
            -not -iname "*_OLD_*" \
            -not -iname "*old*" \
            -printf "%P\n" | sort)

        if [[ ${#app_files[@]} -eq 0 ]]; then
            echo -e "$ERROR No app files found"
            exit 1
        fi

        # Display numbered list (basename only)
        for i in "${!app_files[@]}"; do
            echo "  $((i + 1)). $(basename "${app_files[$i]}")"
        done

        echo ""
        read -p "Select app number (1-${#app_files[@]}): " app_selection

        if [[ ! "$app_selection" =~ ^[0-9]+$ ]] || [[ "$app_selection" -lt 1 ]] || [[ "$app_selection" -gt ${#app_files[@]} ]]; then
            echo -e "$ERROR Invalid selection"
            exit 1
        fi

        FILE_PATH="APPS/${app_files[$((app_selection - 1))]}"
        echo -e "$INFO Selected: $FILE_PATH"
        ;;
    3)
        echo -e "$INFO Selected: Push ANY file"
        echo ""
        echo "Available Groovy files:"

        # Find all .groovy files (exclude deprecated, copies, and old versions)
        mapfile -t all_files < <(find "${HUBITAT_BASE}/SMARTHOME_MAIN" -type f -name "*.groovy" \
            -not -ipath "*deprecated*" \
            -not -name "* copy.groovy" \
            -not -iname "*_OLD_*" \
            -not -iname "*old*" \
            -printf "%P\n" | sort)

        if [[ ${#all_files[@]} -eq 0 ]]; then
            echo -e "$ERROR No Groovy files found"
            exit 1
        fi

        # Display numbered list (basename only)
        for i in "${!all_files[@]}"; do
            echo "  $((i + 1)). $(basename "${all_files[$i]}")"
        done

        echo ""
        read -p "Select file number (1-${#all_files[@]}): " file_selection

        if [[ ! "$file_selection" =~ ^[0-9]+$ ]] || [[ "$file_selection" -lt 1 ]] || [[ "$file_selection" -gt ${#all_files[@]} ]]; then
            echo -e "$ERROR Invalid selection"
            exit 1
        fi

        FILE_PATH="${all_files[$((file_selection - 1))]}"
        echo -e "$INFO Selected: $FILE_PATH"
        ;;
    4)
        cat <<EOF

${BG_BLUE}═══════════════════════════════════════════════════════${NC}
${BG_BLUE}  Help & Information  ${NC}
${BG_BLUE}═══════════════════════════════════════════════════════${NC}

This script pushes Hubitat Groovy code (drivers/apps) to all configured hubs.

WORKFLOW:
  1. Runs bidirectional sync (WSL ↔ Windows, MAIN ↔ instances)
  2. Checks metadata.json in each instance for file mapping
  3. Detects if file is NEW or EXISTING
  4. For NEW: Prompts for Hub ID and code type
  5. For DUPLICATES: Prompts to select which entry to update
  6. Pushes code via HTTP POST to each hub in parallel
  7. Updates metadata.json version numbers

CONFIGURED HUBS:
  - SMARTHOME1: ${HUB_IPS[SMARTHOME1]}
  - SMARTHOME2: ${HUB_IPS[SMARTHOME2]}
  - SMARTHOME3: ${HUB_IPS[SMARTHOME3]}
  - SMARTHOME4: ${HUB_IPS[SMARTHOME4]}

METADATA FILES:
  - /home/elfege/HUBITAT/SMARTHOME1/.hubitat/metadata.json
  - /home/elfege/HUBITAT/SMARTHOME2/.hubitat/metadata.json
  - /home/elfege/HUBITAT/SMARTHOME3/.hubitat/metadata.json
  - /home/elfege/HUBITAT/SMARTHOME4/.hubitat/metadata.json

EOF
        exit 0
        ;;
    5)
        echo -e "$INFO Exiting"
        exit 0
        ;;
    *)
        echo -e "$ERROR Invalid selection"
        exit 1
        ;;
esac

# Validate file path was provided
if [[ -z "$FILE_PATH" ]]; then
    echo -e "$ERROR No file path provided"
    exit 1
fi

# Validate file exists in SMARTHOME_MAIN
MAIN_FILE="${HUBITAT_BASE}/SMARTHOME_MAIN/${FILE_PATH}"
if [[ ! -f "$MAIN_FILE" ]]; then
    echo -e "$ERROR File not found: $MAIN_FILE"
    exit 1
fi

# Lightweight sync: MAIN → instances, then instances → MAIN
quick_sync() {
    echo -e "$SYNCING Quick sync: distributing code from MAIN to instances..."

    # MAIN → all instances (distribute updated code)
    for instance in SMARTHOME1 SMARTHOME2 SMARTHOME3 SMARTHOME4; do
        rsync -auc --delete "${HUBITAT_BASE}/SMARTHOME_MAIN/" "${HUBITAT_BASE}/${instance}/" \
            --exclude='.hubitat/' >/dev/null 2>&1
    done

    # All instances → MAIN (pull any instance-specific changes back)
    for instance in SMARTHOME1 SMARTHOME2 SMARTHOME3 SMARTHOME4; do
        rsync -auc "${HUBITAT_BASE}/${instance}/" "${HUBITAT_BASE}/SMARTHOME_MAIN/" \
            --exclude='.hubitat/' >/dev/null 2>&1
    done

    echo -e "$SUCCESS Sync completed"
}

quick_sync

echo ""
echo -e "$PUSHING Preparing to push to hubs..."

# Phase 1: Check metadata and gather user decisions (serial)
check_metadata_entry() {
    local instance="$1"
    local metadata_file="${HUBITAT_BASE}/${instance}/.hubitat/metadata.json"
    local file_basename=$(basename "$FILE_PATH")

    if [[ ! -f "$metadata_file" ]]; then
        echo -e "$WARNING No metadata.json for $instance - skipping"
        return 1
    fi

    # Find all entries matching the basename
    local matching_entries=$(jq -c --arg basename "$file_basename" '
        [.files[] | select(.filepath | ascii_downcase | endswith($basename | ascii_downcase))]
    ' "$metadata_file")

    local entry_count=$(echo "$matching_entries" | jq 'length')

    if [[ "$entry_count" -eq 0 ]]; then
        # New driver/app - prompt user
        echo ""
        echo -e "$INFO ${instance}: File '$file_basename' not found in metadata.json"
        echo -e "$INFO This appears to be a NEW driver/app."

        read -p "Enter Hub ID (or press ENTER for automatic creation): " hub_id

        # Allow ENTER for automatic ID assignment
        if [[ -z "$hub_id" ]]; then
            hub_id="AUTO"
            echo -e "$INFO ${instance}: Will create new driver/app and auto-assign ID"
        elif [[ ! "$hub_id" =~ ^[0-9]+$ ]]; then
            echo -e "$ERROR ${instance}: Invalid Hub ID - must be numeric or empty"
            return 1
        fi

        read -p "Enter code type (driver/app): " code_type
        code_type=$(echo "$code_type" | tr '[:upper:]' '[:lower:]')
        if [[ "$code_type" != "driver" && "$code_type" != "app" ]]; then
            echo -e "$ERROR ${instance}: Invalid code type '$code_type' - must be 'driver' or 'app'"
            return 1
        fi

        # Store decision: hub_id:code_type:version:is_new
        echo "${hub_id}:${code_type}:1:true"
        return 0

    elif [[ "$entry_count" -gt 1 ]]; then
        # Duplicate entries - prompt user to select
        echo ""
        echo -e "$WARNING ${instance}: Found $entry_count entries with basename '$file_basename'"
        echo "$matching_entries" | jq -r 'to_entries[] | "  \(.key + 1). ID=\(.value.id) Type=\(.value.codeType) Version=\(.value.version) Path=\(.value.filepath)"'

        read -p "Select entry number (1-${entry_count}): " selection
        if [[ ! "$selection" =~ ^[0-9]+$ ]] || [[ "$selection" -lt 1 ]] || [[ "$selection" -gt "$entry_count" ]]; then
            echo -e "$ERROR ${instance}: Invalid selection - skipping this instance"
            return 1
        fi

        local selected_entry=$(echo "$matching_entries" | jq -r ".[$((selection - 1))]")
        local hub_id=$(echo "$selected_entry" | jq -r '.id')
        local code_type=$(echo "$selected_entry" | jq -r '.codeType')
        local version=$(echo "$selected_entry" | jq -r '.version // 1')

        # Store decision: hub_id:code_type:version:is_new:selected_filepath
        local selected_filepath=$(echo "$selected_entry" | jq -r '.filepath')
        echo "${hub_id}:${code_type}:${version}:false:${selected_filepath}"
        return 0

    else
        # Single match found
        local metadata_entry=$(echo "$matching_entries" | jq -r '.[0]')
        local hub_id=$(echo "$metadata_entry" | jq -r '.id')
        local code_type=$(echo "$metadata_entry" | jq -r '.codeType')
        local version=$(echo "$metadata_entry" | jq -r '.version // 1')

        # Store decision: hub_id:code_type:version:is_new
        echo "${hub_id}:${code_type}:${version}:false"
        return 0
    fi
}

# Phase 2: Push to hub using pre-gathered decision (parallel-safe)
push_to_hub() {
    local instance="$1"
    local decision="$2"
    local hub_ip="${HUB_IPS[$instance]}"
    local metadata_file="${HUBITAT_BASE}/${instance}/.hubitat/metadata.json"
    local file_basename=$(basename "$FILE_PATH")

    # Parse decision string
    IFS=':' read -r hub_id code_type version is_new selected_filepath <<< "$decision"
    local new_version=$((version + 1))
    local auto_create=false

    # Handle AUTO hub ID (creation mode)
    if [[ "$hub_id" == "AUTO" ]]; then
        auto_create=true
        # Use id=0 for creation (common pattern - hub will assign actual ID)
        hub_id="0"
        echo -e "$INFO ${instance}: Creating NEW ${code_type} with auto-assigned ID"
    elif [[ "$is_new" == "true" ]]; then
        echo -e "$INFO ${instance}: Creating NEW ${code_type} - ID=${hub_id}, Version=${new_version}"
    else
        echo -e "$INFO ${instance}: Updating existing ${code_type} - ID=${hub_id}, Version=${version} → ${new_version}"
    fi

    # Read source code
    local source_code=$(cat "${HUBITAT_BASE}/${instance}/${FILE_PATH}")

    # Determine endpoint
    local endpoint
    if [[ "$code_type" == "driver" ]]; then
        endpoint="/driver/ajax/update"
    elif [[ "$code_type" == "app" ]]; then
        endpoint="/app/ajax/update"
    else
        echo -e "$ERROR Unknown code type: $code_type"
        return 1
    fi

    # URL-encode the source code
    local encoded_source=$(jq -rn --arg src "$source_code" '$src | @uri')

    # Build POST data
    local post_data="id=${hub_id}&version=${new_version}&source=${encoded_source}"

    echo -e "$NETWORK Pushing to http://${hub_ip}${endpoint}..."

    # Make HTTP request (no auth for now - will add session/cookie support)
    local response=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "$post_data" \
        "http://${hub_ip}${endpoint}" 2>&1)

    # Extract HTTP status code from response
    local http_code=$(echo "$response" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    local response_body=$(echo "$response" | sed 's/HTTP_CODE:[0-9]*$//')

    echo -e "$INFO ${instance}: HTTP Response Code: ${http_code:-UNKNOWN}"
    echo -e "$INFO ${instance}: Response Body: ${response_body:0:200}..."

    if [[ $? -eq 0 && "$http_code" == "200" ]]; then
        echo -e "$SUCCESS ${instance}: Code pushed successfully"

        # For auto-create, try to extract assigned ID from response
        local actual_hub_id="$hub_id"
        if [[ "$auto_create" == "true" ]]; then
            # Try to parse ID from response JSON (format varies by Hubitat version)
            # Common patterns: {"id":123,...} or {"status":"success","id":123,...}
            local extracted_id=$(echo "$response_body" | jq -r '.id // .data.id // empty' 2>/dev/null)

            if [[ -n "$extracted_id" && "$extracted_id" =~ ^[0-9]+$ ]]; then
                actual_hub_id="$extracted_id"
                echo -e "$INFO ${instance}: Hub assigned ID ${actual_hub_id}"
            else
                echo -e "$WARNING ${instance}: Could not extract auto-assigned ID from response"
                echo -e "$WARNING Response: $response_body"
                echo -e "$INFO ${instance}: Using ID 0 in metadata - you may need to manually update"
                actual_hub_id="0"
            fi
        fi

        if [[ "$is_new" == "true" || "$auto_create" == "true" ]]; then
            # Append new entry to metadata.json
            local full_filepath="${HUBITAT_BASE}/${instance}/${FILE_PATH}"
            # Use WSL path format (not Windows paths)
            # Full filepath is already in correct format: /home/elfege/HUBITAT/SMARTHOME{N}/...
            local metadata_filepath="$full_filepath"

            local new_entry=$(jq -n \
                --arg filepath "$metadata_filepath" \
                --arg codeType "$code_type" \
                --argjson id "$actual_hub_id" \
                --argjson version "$new_version" \
                '{filepath: $filepath, codeType: $codeType, id: $id, version: $version}')

            jq --argjson entry "$new_entry" '.files += [$entry]' "$metadata_file" > "${metadata_file}.tmp" && mv "${metadata_file}.tmp" "$metadata_file"
            echo -e "$SAVED ${instance}: New entry added to metadata.json (ID: ${actual_hub_id})"
        else
            # Update metadata.json version (for duplicate case, use selected_filepath if available)
            if [[ -n "$selected_filepath" ]]; then
                jq --arg filepath "$selected_filepath" --argjson newver "$new_version" '
                    .files |= map(
                        if .filepath == $filepath
                        then .version = $newver
                        else .
                        end
                    )
                ' "$metadata_file" > "${metadata_file}.tmp" && mv "${metadata_file}.tmp" "$metadata_file"
            else
                jq --arg basename "$file_basename" --argjson newver "$new_version" '
                    .files |= map(
                        if (.filepath | ascii_downcase | endswith($basename | ascii_downcase))
                        then .version = $newver
                        else .
                        end
                    )
                ' "$metadata_file" > "${metadata_file}.tmp" && mv "${metadata_file}.tmp" "$metadata_file"
            fi
            echo -e "$SAVED ${instance}: metadata.json updated (version ${new_version})"
        fi
    else
        echo -e "$ERROR ${instance}: Failed to push code"
        echo "$response"
        return 1
    fi
}

# PHASE 1: Serial metadata checks and user prompting
echo -e "$INFO Checking metadata for all hubs..."
declare -A HUB_DECISIONS

for instance in SMARTHOME1 SMARTHOME2 SMARTHOME3 SMARTHOME4; do
    decision=$(check_metadata_entry "$instance")
    if [[ $? -eq 0 ]]; then
        HUB_DECISIONS[$instance]="$decision"
    else
        echo -e "$WARNING ${instance}: Skipping due to metadata check failure"
    fi
done

# Verify we have at least one hub to push to
if [[ ${#HUB_DECISIONS[@]} -eq 0 ]]; then
    echo -e "$ERROR No hubs available for push - exiting"
    exit 1
fi

echo ""
echo -e "$INFO Metadata checks complete. Proceeding with parallel push..."

# PHASE 2: Parallel push to hubs using pre-gathered decisions
declare -a PIDS=()
tmp_results="/tmp/push_to_hub_results_$$"
mkdir -p "$tmp_results"

for instance in "${!HUB_DECISIONS[@]}"; do
    decision="${HUB_DECISIONS[$instance]}"

    echo ""
    echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"
    echo -e "${BG_BLUE}  ${instance}  ${NC}"
    echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"

    {
        result_file="$tmp_results/${instance}.result"

        if push_to_hub "$instance" "$decision"; then
            echo "SUCCESS" > "$result_file"
            echo -e "$SUCCESS Push completed for $instance"
        else
            echo "FAILED" > "$result_file"
            echo -e "$ERROR Push failed for $instance"
        fi
    } &

    pid="$!"
    PIDS+=("$pid")
done

# Wait for all parallel pushes to complete
wait "${PIDS[@]}"

# Count results
success_count=0
fail_count=0
for result_file in "$tmp_results"/*.result; do
    if [[ -f "$result_file" ]]; then
        if grep -q "SUCCESS" "$result_file"; then
            ((success_count++))
        else
            ((fail_count++))
        fi
    fi
done

# Cleanup temp results
rm -rf "$tmp_results"

echo ""
echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"
echo -e "Push Summary"
echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"
echo -e "Successful: $success_count"
echo -e "Failed: $fail_count"
echo ""
echo -e "$COMPLETED All operations completed"

# Return non-zero if any pushes failed
[[ "$fail_count" -eq 0 ]]
