#!/bin/bash
# hubitat_backup.sh

# Dictionary of Hubitat hubs in the format "hub_name:ip"
declare -A HUBS=(
    ["home1"]="192.168.10.69"
    ["home2"]="192.168.10.70"
    ["home3"]="192.168.10.71"
    ["home4"]="192.168.10.72"
)

# Backup directory
BACKUP_DIR="/mnt/f/HUBITAT_BACKUPS"

# Log file
LOG_FILE="$BACKUP_DIR/backup_log.txt"

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR" || { echo "Failed to create backup directory $BACKUP_DIR"; exit 1; }

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

create_cron_job() {
    CRON_JOB="0 5 * * * /bin/bash $(realpath $0)"
    (crontab -l 2>/dev/null; echo "$CRON_JOB") | crontab -
    log "Cron job added: $CRON_JOB"
}

backup_hub() {
    local hub_name="$1"
    local hub_ip="$2"
    local timestamp
    timestamp=$(date '+%m_%d_%Y_T_%H_%M_%S')
    local backup_file="$hub_ip-$timestamp.lzf"
    local hub_backup_dir="$BACKUP_DIR/$hub_name"

    # Create subdirectory for the hub
    mkdir -p "$hub_backup_dir" || { log "Failed to create directory $hub_backup_dir"; return 1; }

    # Perform backup
    local url="http://$hub_ip/hub/backupDB?fileName=latest"
    if curl -s -o "$hub_backup_dir/$backup_file" "$url"; then
        log "Backup successful for $hub_name ($hub_ip) -> $hub_backup_dir/$backup_file"
    else
        log "Failed to download backup for hub $hub_name ($hub_ip)"
        return 1
    fi
}

main() {
    log "Backup process started."

    for hub_name in "${!HUBS[@]}"; do
        hub_ip="${HUBS[$hub_name]}"
        log "Starting backup for hub $hub_name with IP $hub_ip."
        if backup_hub "$hub_name" "$hub_ip"; then
            log "Backup completed for hub $hub_name ($hub_ip)."
        else
            log "Backup failed for hub $hub_name ($hub_ip)."
        fi
    done

    log "Backup process completed."

    # Check if the script is already scheduled in cron
    if ! crontab -l 2>/dev/null | grep -q "$(realpath $0)"; then
        create_cron_job
    else
        log "Cron job already exists."
    fi
}

main
