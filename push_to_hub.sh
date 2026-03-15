#!/bin/bash
# push_to_hub.sh
# Pushes Groovy code to Hubitat hubs via HTTP API
# Queries hubs directly for installed drivers/apps — no metadata.json dependency
#
# Usage:
#   ./push_to_hub.sh                          # Interactive menu
#   ./push_to_hub.sh DRIVERS/MY_DRIVER.groovy # Direct push (auto-detects code type)
#   ./push_to_hub.sh --list                   # Show what's installed where

clear

. ~/.env.colors

# PID-based locking to prevent duplicate instances
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
SCRIPT_NAME_NO_EXT="${SCRIPT_NAME%.*}"
export MAIN_PID=$$
PID_FILE="/tmp/${SCRIPT_NAME_NO_EXT}.pid"
touch "$PID_FILE"

PREVIOUS_PID="$(cat "$PID_FILE")"
if [ -n "$PREVIOUS_PID" ]; then
    if kill -0 "${PREVIOUS_PID}" 2>/dev/null; then
        echo -e "$ERROR $0 already running (PID: $PREVIOUS_PID)."
        exit 0
    fi
fi
echo "$MAIN_PID" >"$PID_FILE"

# Hub configuration
declare -A HUB_IPS=(
    ["SMARTHOME1"]="192.168.10.69"
    ["SMARTHOME2"]="192.168.10.70"
    ["SMARTHOME3"]="192.168.10.71"
    ["SMARTHOME4"]="192.168.10.72"
)

HUBITAT_BASE="/home/elfege/HUBITAT"
HUB_ORDER=(SMARTHOME1 SMARTHOME2 SMARTHOME3 SMARTHOME4)

cleanup() { : >"$PID_FILE"; }
trap cleanup EXIT

# ──────────────────────────────────────────────────────────
# Extract the definition name from a Groovy source file
# Handles both single-line and multi-line definition blocks
# ──────────────────────────────────────────────────────────
extract_definition_name() {
    local file="$1"
    # Grab lines around 'definition' keyword and extract the name value
    # Works for both single-line and multi-line definition blocks
    grep -A5 'definition' "$file" | grep -oP 'name\s*:\s*"\K[^"]+' | head -1
}

# ──────────────────────────────────────────────────────────
# Detect code type (driver vs app) from file path
# ──────────────────────────────────────────────────────────
detect_code_type() {
    local file_path="$1"
    if echo "$file_path" | grep -qi "DRIVER"; then
        echo "driver"
    elif echo "$file_path" | grep -qi "APP"; then
        echo "app"
    else
        # Fallback: check file content for driver vs app indicators
        if grep -q 'capability\s' "$HUBITAT_BASE/SMARTHOME_MAIN/$file_path" 2>/dev/null; then
            echo "driver"
        elif grep -q 'dynamicPage\|preferences\s*{' "$HUBITAT_BASE/SMARTHOME_MAIN/$file_path" 2>/dev/null; then
            echo "app"
        else
            echo ""
        fi
    fi
}

# ──────────────────────────────────────────────────────────
# Query a hub for all user-installed drivers or apps
# Returns JSON array: [{id, name, version}, ...]
# ──────────────────────────────────────────────────────────
query_hub_installed() {
    local hub_ip="$1"
    local code_type="$2" # "driver" or "app"

    local data
    data=$(curl -s --connect-timeout 3 --max-time 10 \
        "http://${hub_ip}/${code_type}/list/data" 2>/dev/null)

    if [[ -z "$data" ]] || ! echo "$data" | jq empty 2>/dev/null; then
        echo "[]"
        return 1
    fi

    # Return only user-installed entries
    echo "$data" | jq -c '[.[] | select(.type=="usr") | {id, name, version}]'
}

# ──────────────────────────────────────────────────────────
# Find matching entry on a hub by definition name
# Returns: id:version or empty string
# ──────────────────────────────────────────────────────────
find_on_hub() {
    local hub_ip="$1"
    local code_type="$2"
    local def_name="$3"

    local installed
    installed=$(query_hub_installed "$hub_ip" "$code_type")

    if [[ "$installed" == "[]" ]]; then
        return 1
    fi

    # Exact match first
    local match
    match=$(echo "$installed" | jq -r --arg name "$def_name" \
        '.[] | select(.name == $name) | "\(.id):\(.version)"' | head -1)

    if [[ -n "$match" ]]; then
        echo "$match"
        return 0
    fi

    # Case-insensitive match as fallback
    match=$(echo "$installed" | jq -r --arg name "$def_name" \
        '.[] | select(.name | ascii_downcase == ($name | ascii_downcase)) | "\(.id):\(.version)"' | head -1)

    if [[ -n "$match" ]]; then
        echo "$match"
        return 0
    fi

    return 1
}

# ──────────────────────────────────────────────────────────
# Push source code to a single hub
# ──────────────────────────────────────────────────────────
push_to_single_hub() {
    local instance="$1"
    local hub_ip="$2"
    local code_type="$3"
    local hub_id="$4"
    local hub_version="$5"
    local source_file="$6"

    local endpoint="/${code_type}/ajax/update"

    # URL-encode source code
    local source_code
    source_code=$(cat "$source_file")
    local encoded_source
    encoded_source=$(jq -rn --arg src "$source_code" '$src | @uri')

    # POST with current version — hub auto-increments
    local post_data="id=${hub_id}&version=${hub_version}&source=${encoded_source}"

    local response
    response=$(curl -s --connect-timeout 5 --max-time 30 -X POST \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "$post_data" \
        "http://${hub_ip}${endpoint}" 2>&1)

    # Parse response
    local status
    status=$(echo "$response" | jq -r '.status // empty' 2>/dev/null)
    local new_version
    new_version=$(echo "$response" | jq -r '.version // empty' 2>/dev/null)
    local error_msg
    error_msg=$(echo "$response" | jq -r '.errorMessage // empty' 2>/dev/null)

    if [[ "$status" == "success" ]]; then
        echo -e "  $SUCCESS ${instance} (${hub_ip}): pushed OK  [id=${hub_id}, v${hub_version} -> v${new_version}]"
        return 0
    elif [[ -n "$error_msg" ]]; then
        echo -e "  $ERROR ${instance} (${hub_ip}): ${error_msg}"
        return 1
    else
        echo -e "  $ERROR ${instance} (${hub_ip}): unexpected response: ${response:0:200}"
        return 1
    fi
}

# ──────────────────────────────────────────────────────────
# Main push logic: resolve name, find on hubs, push
# ──────────────────────────────────────────────────────────
do_push() {
    local file_path="$1" # relative to SMARTHOME_MAIN, e.g. DRIVERS/SPLIT_AC_IR_CONTROLLER.groovy
    local source_file="${HUBITAT_BASE}/SMARTHOME_MAIN/${file_path}"

    if [[ ! -f "$source_file" ]]; then
        echo -e "$ERROR File not found: $source_file"
        return 1
    fi

    # Extract definition name from Groovy source
    local def_name
    def_name=$(extract_definition_name "$source_file")
    if [[ -z "$def_name" ]]; then
        echo -e "$ERROR Could not extract definition name from: $source_file"
        echo -e "$INFO   Expected pattern: definition(name: \"...\", ...)"
        return 1
    fi

    # Detect code type
    local code_type
    code_type=$(detect_code_type "$file_path")
    if [[ -z "$code_type" ]]; then
        echo -e "$WARNING Could not auto-detect code type for: $file_path"
        read -p "  Enter code type (driver/app): " code_type
        code_type=$(echo "$code_type" | tr '[:upper:]' '[:lower:]')
        if [[ "$code_type" != "driver" && "$code_type" != "app" ]]; then
            echo -e "$ERROR Invalid code type"
            return 1
        fi
    fi

    echo ""
    echo -e "${BG_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "  File: $(basename "$file_path")"
    echo -e "  Name: \"${def_name}\""
    echo -e "  Type: ${code_type}"
    echo -e "${BG_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo ""

    # Scan all hubs to find where this driver/app is installed
    echo -e "$SYNCING Scanning hubs for \"${def_name}\"..."
    echo ""

    declare -A HUB_MATCHES=()
    local found_count=0

    for instance in "${HUB_ORDER[@]}"; do
        local hub_ip="${HUB_IPS[$instance]}"
        local match
        match=$(find_on_hub "$hub_ip" "$code_type" "$def_name")

        if [[ $? -eq 0 && -n "$match" ]]; then
            HUB_MATCHES[$instance]="$match"
            local mid mver
            IFS=':' read -r mid mver <<< "$match"
            echo -e "  $SUCCESS ${instance} (${hub_ip}): found  [id=${mid}, version=${mver}]"
            ((found_count++))
        else
            echo -e "  $WARNING ${instance} (${hub_ip}): not installed"
        fi
    done

    echo ""

    if [[ $found_count -eq 0 ]]; then
        echo -e "$ERROR \"${def_name}\" not found on any hub."
        echo -e "$INFO   Install it manually on a hub first, then re-run this script."
        return 1
    fi

    # Confirm push
    echo -e "$PUSHING Ready to push to ${found_count} hub(s). Proceed? [Y/n] "
    read -r confirm
    if [[ "$confirm" =~ ^[Nn] ]]; then
        echo -e "$INFO Push cancelled."
        return 0
    fi

    echo ""

    # Push to all matching hubs in parallel
    local success_count=0
    local fail_count=0
    local tmp_results="/tmp/push_to_hub_results_$$"
    mkdir -p "$tmp_results"
    declare -a PIDS=()

    for instance in "${HUB_ORDER[@]}"; do
        if [[ -z "${HUB_MATCHES[$instance]}" ]]; then
            continue
        fi

        local hub_ip="${HUB_IPS[$instance]}"
        local hub_id hub_version
        IFS=':' read -r hub_id hub_version <<< "${HUB_MATCHES[$instance]}"

        {
            if push_to_single_hub "$instance" "$hub_ip" "$code_type" "$hub_id" "$hub_version" "$source_file"; then
                echo "SUCCESS" > "$tmp_results/${instance}.result"
            else
                echo "FAILED" > "$tmp_results/${instance}.result"
            fi
        } &
        PIDS+=("$!")
    done

    wait "${PIDS[@]}"

    # Count results
    for result_file in "$tmp_results"/*.result; do
        if [[ -f "$result_file" ]]; then
            if grep -q "SUCCESS" "$result_file"; then
                ((success_count++))
            else
                ((fail_count++))
            fi
        fi
    done

    rm -rf "$tmp_results"

    echo ""
    echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"
    echo -e "  Results: ${success_count} OK / ${fail_count} failed"
    echo -e "${BG_BLUE}═══════════════════════════════════════${NC}"

    [[ "$fail_count" -eq 0 ]]
}

# ──────────────────────────────────────────────────────────
# --list mode: show what's installed where
# ──────────────────────────────────────────────────────────
do_list() {
    echo ""
    echo -e "${BG_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BG_BLUE}  Installed User Code Across All Hubs  ${NC}"
    echo -e "${BG_BLUE}═══════════════════════════════════════════════════════${NC}"
    echo ""

    for instance in "${HUB_ORDER[@]}"; do
        local hub_ip="${HUB_IPS[$instance]}"
        echo -e "${BG_BLUE} ${instance} (${hub_ip}) ${NC}"

        echo "  DRIVERS:"
        local drivers
        drivers=$(query_hub_installed "$hub_ip" "driver")
        echo "$drivers" | jq -r '.[] | "    [\(.id)] \(.name) (v\(.version))"' 2>/dev/null

        echo "  APPS:"
        local apps
        apps=$(query_hub_installed "$hub_ip" "app")
        echo "$apps" | jq -r '.[] | "    [\(.id)] \(.name) (v\(.version))"' 2>/dev/null

        echo ""
    done
}

# ──────────────────────────────────────────────────────────
# Interactive file selection
# ──────────────────────────────────────────────────────────
select_file() {
    local search_dir="$1"   # "DRIVERS", "APPS", or "" for all
    local label="$2"

    local base_dir="${HUBITAT_BASE}/SMARTHOME_MAIN"
    if [[ -n "$search_dir" ]]; then
        base_dir="${base_dir}/${search_dir}"
    fi

    # All interactive output goes to stderr so stdout is clean for the result
    echo "" >&2
    echo "Available ${label}:" >&2
    echo "" >&2

    mapfile -t files < <(find "$base_dir" -type f -name "*.groovy" \
        -not -ipath "*deprecated*" \
        -not -name "* copy.groovy" \
        -not -iname "*_OLD_*" \
        -not -ipath "*backup*" \
        -printf "%P\n" | sort)

    if [[ ${#files[@]} -eq 0 ]]; then
        echo -e "$ERROR No Groovy files found in ${base_dir}" >&2
        return 1
    fi

    for i in "${!files[@]}"; do
        local full_path="${base_dir}/${files[$i]}"
        local def_name
        def_name=$(extract_definition_name "$full_path" 2>/dev/null)
        if [[ -n "$def_name" ]]; then
            printf "  %2d. %-45s  (%s)\n" "$((i + 1))" "$(basename "${files[$i]}")" "$def_name" >&2
        else
            printf "  %2d. %s\n" "$((i + 1))" "$(basename "${files[$i]}")" >&2
        fi
    done

    echo "" >&2
    echo "  Enter number(s) comma-separated (e.g. 1,3,7) or 'all'" >&2
    read -p "Select (1-${#files[@]}): " sel

    # Check for spaces in input (common mistake)
    if [[ "$sel" == *" "* ]]; then
        echo -e "$ERROR No spaces allowed — use commas only (e.g. 1,3,7)" >&2
        return 1
    fi

    # Handle "all"
    if [[ "${sel,,}" == "all" ]]; then
        for i in "${!files[@]}"; do
            if [[ -n "$search_dir" ]]; then
                echo "${search_dir}/${files[$i]}"
            else
                echo "${files[$i]}"
            fi
        done
        return 0
    fi

    # Parse comma-separated numbers
    IFS=',' read -ra selections <<< "$sel"
    for s in "${selections[@]}"; do
        if [[ ! "$s" =~ ^[0-9]+$ ]] || [[ "$s" -lt 1 ]] || [[ "$s" -gt ${#files[@]} ]]; then
            echo -e "$ERROR Invalid selection: '$s' (must be 1-${#files[@]})" >&2
            return 1
        fi
        if [[ -n "$search_dir" ]]; then
            echo "${search_dir}/${files[$((s - 1))]}"
        else
            echo "${files[$((s - 1))]}"
        fi
    done
}

# ──────────────────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────────────────

# Handle CLI arguments
if [[ "$1" == "--list" ]]; then
    do_list
    exit $?
fi

if [[ -n "$1" && "$1" != "--"* ]]; then
    # Direct file path provided
    do_push "$1"
    exit $?
fi

# Interactive menu
cat <<EOF

$(echo -e "${BG_BLUE}")═══════════════════════════════════════════════════════$(echo -e "${NC}")
$(echo -e "${BG_BLUE}")  Hubitat Code Push Utility                          $(echo -e "${NC}")
$(echo -e "${BG_BLUE}")═══════════════════════════════════════════════════════$(echo -e "${NC}")

  1. Push a DRIVER
  2. Push an APP
  3. Push ANY Groovy file
  4. List installed code on all hubs
  5. Exit

  Workflow:
    - Parses definition name from Groovy source
    - Queries each hub's API for matching installed code
    - Pushes to all hubs where it's already installed
    - No metadata.json needed — hubs are source of truth

EOF

read -p "Enter selection (1-5): " selection

case "$selection" in
    1)
        FILE_PATHS=$(select_file "DRIVERS" "drivers")
        [[ $? -ne 0 ]] && exit 1
        while IFS= read -r fp; do
            do_push "$fp"
        done <<< "$FILE_PATHS"
        ;;
    2)
        FILE_PATHS=$(select_file "APPS" "apps")
        [[ $? -ne 0 ]] && exit 1
        while IFS= read -r fp; do
            do_push "$fp"
        done <<< "$FILE_PATHS"
        ;;
    3)
        FILE_PATHS=$(select_file "" "Groovy files")
        [[ $? -ne 0 ]] && exit 1
        while IFS= read -r fp; do
            do_push "$fp"
        done <<< "$FILE_PATHS"
        ;;
    4)
        do_list
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
