#!/bin/bash
# hubitat_backup.sh
# Automated Hubitat backup script with rotation and gold copy features
# Uses SSH socket management from ~/.bash_utils for efficient remote operations

set -euo pipefail

# Source bash utilities for SSH socket management
if [[ -f ~/.bash_utils ]]; then
    source ~/.bash_utils
else
    echo "ERROR: ~/.bash_utils not found. SSH socket functions unavailable."
    exit 1
fi

# Dictionary of Hubitat hubs in the format "hub_name:ip"
declare -A HUBS=(
    ["home1"]="192.168.10.69"
    ["home2"]="192.168.10.70"
    ["home3"]="192.168.10.71"
    ["home4"]="192.168.10.72"
)

# Configuration
BACKUP_DIR="/mnt/THE_BIG_DRIVE/HUBITAT_BACKUPS"
SSH_HOST="dellserver"
LOCAL_TMP_DIR="/tmp/hubitat_backups_$$"
MAX_RETENTION_DAYS=120
GOLD_COPY_DAY=1  # Day of month for gold copy creation

# Log file (remote)
LOG_FILE="$BACKUP_DIR/backup_log.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    local message="$1"
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} - ${message}"
    # Also append to remote log file via SSH
    if [[ -n "${SSH_SOCKET_ACTIVE:-}" ]]; then
        ssh_with_socket "$SSH_HOST" "echo '${timestamp} - ${message}' >> '$LOG_FILE'" 2>/dev/null || true
    fi
}

log_error() {
    log "${RED}ERROR: $1${NC}"
}

log_success() {
    log "${GREEN}$1${NC}"
}

log_warn() {
    log "${YELLOW}WARNING: $1${NC}"
}

cleanup() {
    log "Cleaning up temporary files..."
    rm -rf "$LOCAL_TMP_DIR"
    if [[ -n "${SSH_SOCKET_ACTIVE:-}" ]]; then
        ssh_socket_stop "$SSH_HOST"
    fi
}

trap cleanup EXIT

setup_ssh_connection() {
    log "Establishing SSH connection to $SSH_HOST..."

    if ssh_socket_start "$SSH_HOST" 3600; then
        SSH_SOCKET_ACTIVE=1
        log_success "SSH socket established for $SSH_HOST"
        return 0
    else
        log_error "Failed to establish SSH socket for $SSH_HOST"
        return 1
    fi
}

ensure_remote_directory() {
    local dir="$1"
    log "Ensuring remote directory exists: $dir"
    ssh_with_socket "$SSH_HOST" "mkdir -p '$dir'" || {
        log_error "Failed to create directory: $dir"
        return 1
    }
}

is_gold_copy_day() {
    local current_day
    current_day=$(date '+%-d')  # Day of month without leading zero
    [[ "$current_day" -eq "$GOLD_COPY_DAY" ]]
}

gold_copy_exists_this_month() {
    local hub_name="$1"
    local hub_backup_dir="$BACKUP_DIR/$hub_name"
    local current_month_year
    current_month_year=$(date '+%m_%Y')

    # Check if a gold copy for this month already exists
    local gold_exists
    gold_exists=$(ssh_with_socket "$SSH_HOST" "ls -1 '$hub_backup_dir' 2>/dev/null | grep -c '_gold_copy_.*${current_month_year}' || true")
    [[ "$gold_exists" -gt 0 ]]
}

create_gold_copy() {
    local hub_name="$1"
    local source_file="$2"
    local hub_backup_dir="$BACKUP_DIR/$hub_name/gold_copies"
    local timestamp
    timestamp=$(date '+%m_%d_%Y_T_%H_%M_%S')
    local gold_file="${hub_name}_gold_copy_${timestamp}.lzf"

    ensure_remote_directory "$hub_backup_dir"

    log "Creating gold copy for $hub_name..."
    if scp -o ControlPath="/tmp/ssh-socket-${SSH_HOST}" "$source_file" "${SSH_HOST}:${hub_backup_dir}/${gold_file}"; then
        log_success "Gold copy created: $hub_backup_dir/$gold_file"
        return 0
    else
        log_error "Failed to create gold copy for $hub_name"
        return 1
    fi
}

rotate_old_backups() {
    local hub_name="$1"
    local hub_backup_dir="$BACKUP_DIR/$hub_name"

    log "Rotating backups older than $MAX_RETENTION_DAYS days for $hub_name (excluding gold copies)..."

    # Find and delete regular backups older than MAX_RETENTION_DAYS days
    # Exclude gold_copies directory and any files with 'gold_copy' in name
    local deleted_count
    deleted_count=$(ssh_with_socket "$SSH_HOST" "
        find '$hub_backup_dir' -maxdepth 1 -type f -name '*.lzf' ! -name '*gold_copy*' -mtime +$MAX_RETENTION_DAYS -delete -print 2>/dev/null | wc -l
    " || echo "0")

    if [[ "$deleted_count" -gt 0 ]]; then
        log "Deleted $deleted_count old backup(s) for $hub_name"
    else
        log "No backups older than $MAX_RETENTION_DAYS days found for $hub_name"
    fi
}

backup_hub() {
    local hub_name="$1"
    local hub_ip="$2"
    local timestamp
    timestamp=$(date '+%m_%d_%Y_T_%H_%M_%S')
    local backup_file="${hub_ip}-${timestamp}.lzf"
    local hub_backup_dir="$BACKUP_DIR/$hub_name"
    local local_backup_file="$LOCAL_TMP_DIR/$backup_file"

    # Create local temp directory
    mkdir -p "$LOCAL_TMP_DIR"

    # Ensure remote hub directory exists
    ensure_remote_directory "$hub_backup_dir"

    # Download backup from hub (this creates a NEW backup and downloads it)
    local url="http://$hub_ip/hub/backupDB?fileName=latest"
    log "Triggering backup creation and download for $hub_name ($hub_ip)..."

    if curl -s -f --connect-timeout 30 --max-time 120 -o "$local_backup_file" "$url"; then
        # Verify file was downloaded and has content
        if [[ -f "$local_backup_file" && -s "$local_backup_file" ]]; then
            log_success "Backup downloaded successfully for $hub_name"

            # Transfer to remote server
            log "Transferring backup to $SSH_HOST..."
            if scp -o ControlPath="/tmp/ssh-socket-${SSH_HOST}" "$local_backup_file" "${SSH_HOST}:${hub_backup_dir}/${backup_file}"; then
                log_success "Backup transferred: $hub_backup_dir/$backup_file"

                # Create gold copy if it's gold copy day and one doesn't exist this month
                if is_gold_copy_day && ! gold_copy_exists_this_month "$hub_name"; then
                    create_gold_copy "$hub_name" "$local_backup_file"
                fi

                # Rotate old backups
                rotate_old_backups "$hub_name"

                # Cleanup local temp file
                rm -f "$local_backup_file"
                return 0
            else
                log_error "Failed to transfer backup for $hub_name to $SSH_HOST"
                return 1
            fi
        else
            log_error "Downloaded backup file is empty or missing for $hub_name"
            return 1
        fi
    else
        log_error "Failed to download backup from hub $hub_name ($hub_ip)"
        return 1
    fi
}

create_cron_job() {
    local script_path
    script_path=$(realpath "$0")
    local cron_job="0 6 * * * /bin/bash $script_path >> /tmp/hubitat_backup_cron.log 2>&1"

    # Check if cron job already exists
    if crontab -l 2>/dev/null | grep -qF "$script_path"; then
        # Update existing cron job to new time
        log "Updating existing cron job to 6 AM..."
        (crontab -l 2>/dev/null | grep -vF "$script_path"; echo "$cron_job") | crontab -
    else
        # Add new cron job
        log "Adding new cron job for daily backup at 6 AM..."
        (crontab -l 2>/dev/null; echo "$cron_job") | crontab -
    fi
    log_success "Cron job configured: $cron_job"
}

show_status() {
    log "=== Hubitat Backup Status ==="

    for hub_name in "${!HUBS[@]}"; do
        local hub_backup_dir="$BACKUP_DIR/$hub_name"
        log "--- $hub_name ---"

        # Count regular backups
        local regular_count
        regular_count=$(ssh_with_socket "$SSH_HOST" "find '$hub_backup_dir' -maxdepth 1 -type f -name '*.lzf' ! -name '*gold_copy*' 2>/dev/null | wc -l" || echo "0")
        log "  Regular backups: $regular_count"

        # Count gold copies
        local gold_count
        gold_count=$(ssh_with_socket "$SSH_HOST" "find '$hub_backup_dir/gold_copies' -type f -name '*gold_copy*.lzf' 2>/dev/null | wc -l" || echo "0")
        log "  Gold copies: $gold_count"

        # Show latest backup
        local latest
        latest=$(ssh_with_socket "$SSH_HOST" "ls -1t '$hub_backup_dir'/*.lzf 2>/dev/null | head -1 | xargs basename 2>/dev/null" || echo "none")
        log "  Latest backup: $latest"
    done
}

main() {
    log "=============================================="
    log "Hubitat Backup Process Started"
    log "=============================================="
    log "Backup destination: $SSH_HOST:$BACKUP_DIR"
    log "Retention period: $MAX_RETENTION_DAYS days"
    log "Gold copy day: Day $GOLD_COPY_DAY of each month"

    # Setup SSH connection
    if ! setup_ssh_connection; then
        log_error "Cannot establish SSH connection. Aborting."
        exit 1
    fi

    # Ensure base backup directory exists
    ensure_remote_directory "$BACKUP_DIR"

    local success_count=0
    local fail_count=0

    for hub_name in "${!HUBS[@]}"; do
        local hub_ip="${HUBS[$hub_name]}"
        log "----------------------------------------------"
        log "Processing hub: $hub_name ($hub_ip)"

        if backup_hub "$hub_name" "$hub_ip"; then
            log_success "Backup completed for $hub_name"
            ((success_count++))
        else
            log_error "Backup failed for $hub_name"
            ((fail_count++))
        fi
    done

    log "=============================================="
    log "Backup Process Summary"
    log "=============================================="
    log "Successful: $success_count"
    log "Failed: $fail_count"

    # Show status
    show_status

    # Setup/update cron job
    if ! crontab -l 2>/dev/null | grep -qF "$(realpath "$0")"; then
        create_cron_job
    else
        log "Cron job already configured."
    fi

    log "=============================================="
    log "Hubitat Backup Process Completed"
    log "=============================================="

    # Return non-zero if any backups failed
    [[ "$fail_count" -eq 0 ]]
}

# Handle command line arguments
case "${1:-}" in
    --status)
        setup_ssh_connection
        show_status
        ;;
    --setup-cron)
        create_cron_job
        ;;
    --help)
        echo "Usage: $0 [OPTIONS]"
        echo ""
        echo "Options:"
        echo "  --status      Show backup status for all hubs"
        echo "  --setup-cron  Setup/update cron job for daily backups at 6 AM"
        echo "  --help        Show this help message"
        echo ""
        echo "Without arguments, runs the full backup process."
        ;;
    *)
        main
        ;;
esac
