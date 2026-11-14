#!/usr/bin/env bash
#
# Sauvegarde de la base et des CV.
#
# À faire tourner sur le serveur, par cron. Perdre les CV d'un client qui paie n'est pas une
# panne qu'on répare : c'est la fin de la relation commerciale, et une violation de données
# à déclarer. Tant que ce script n'est pas planifié, il n'y a pas de sauvegarde du tout.
#
# Installation (en root sur le serveur) :
#
#   cp scripts/backup.sh /usr/local/bin/skulvi-backup
#   chmod +x /usr/local/bin/skulvi-backup
#   crontab -e
#   # sauvegarde chaque nuit à 3 h 15
#   15 3 * * * /usr/local/bin/skulvi-backup >> /var/log/skulvi-backup.log 2>&1
#
# Restauration de la base :
#
#   gunzip -c base-2026-11-07.sql.gz | docker exec -i skulvi-db psql -U "$DB_USER" -d "$DB_NAME"
#
# Restauration des CV (remplacer le nom du volume par celui que le script affiche) :
#
#   docker run --rm -v skulvi_skulvi_cvs:/data -v "$PWD":/backup alpine \
#     tar xzf /backup/cvs-2026-11-07.tar.gz -C /data
#
set -euo pipefail

# -------------------------------------------------------------------- réglages

APP_DIR="${APP_DIR:-/opt/skulvi}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/skulvi}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

# Chemin du stockage des CV dans le conteneur. Sert à retrouver le volume, quel que soit
# son nom : voir plus bas.
CV_PATH="${CV_PATH:-/data/cvs}"

DATE="$(date +%Y-%m-%d_%H%M)"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { log "ÉCHEC : $*"; exit 1; }

# Premier conteneur existant parmi ceux proposés.
premier_conteneur() {
  for nom in "$@"; do
    if docker inspect "$nom" >/dev/null 2>&1; then
      printf '%s' "$nom"
      return 0
    fi
  done
  return 1
}

# -------------------------------------------------------------------- contrôles

[ -f "$APP_DIR/.env" ] || fail "fichier $APP_DIR/.env introuvable"

# shellcheck disable=SC1091
set -a; . "$APP_DIR/.env"; set +a

: "${DB_USER:?DB_USER absent de .env}"
: "${DB_NAME:?DB_NAME absent de .env}"

#
# Les noms diffèrent entre la production et le développement, et ce script doit marcher
# sur les deux sans réglage.
#
#   serveur         : skulvi-db     / skulvi-backend
#   poste de dev    : postgres      / springboot-app
#
# Les deviner est préférable à les écrire en dur : une sauvegarde qui échoue parce qu'un
# conteneur a été renommé ne se remarque qu'au moment d'une restauration, c'est-à-dire
# trop tard.
#
DB_CONTAINER="${DB_CONTAINER:-$(premier_conteneur skulvi-db postgres || true)}"
APP_CONTAINER="${APP_CONTAINER:-$(premier_conteneur skulvi-backend springboot-app || true)}"

[ -n "$DB_CONTAINER" ] \
  || fail "aucun conteneur de base trouvé (essayés : skulvi-db, postgres). Posez DB_CONTAINER."
[ -n "$APP_CONTAINER" ] \
  || fail "aucun conteneur applicatif trouvé (essayés : skulvi-backend, springboot-app). Posez APP_CONTAINER."

#
# Le volume des CV est lu sur le conteneur lui-même, et non écrit en dur.
#
# Compose préfixe les volumes du nom du projet : « skulvi_cvs » déclaré dans le fichier
# devient « skulvi_skulvi_cvs » une fois créé, et ce préfixe dépend du nom du répertoire.
# Interroger le conteneur donne le vrai nom sans avoir à reconstituer cette règle.
#
if [ -z "${CV_VOLUME:-}" ]; then
  CV_VOLUME="$(docker inspect "$APP_CONTAINER" \
    --format '{{range .Mounts}}{{if eq .Destination "'"$CV_PATH"'"}}{{.Name}}{{end}}{{end}}')"
fi

[ -n "$CV_VOLUME" ] \
  || fail "aucun volume monté sur $CV_PATH dans « $APP_CONTAINER ». Posez CV_VOLUME."

log "base : conteneur « $DB_CONTAINER » · CV : volume « $CV_VOLUME »"

mkdir -p "$BACKUP_DIR"

# -------------------------------------------------------------------- base

DB_FILE="$BACKUP_DIR/base-$DATE.sql.gz"
log "sauvegarde de la base vers $DB_FILE"

# Écriture dans un fichier temporaire puis renommage : une sauvegarde interrompue ne doit
# jamais laisser derrière elle un fichier qui a l'air valide.
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" \
  | gzip > "$DB_FILE.partiel" \
  || fail "pg_dump a échoué"

mv "$DB_FILE.partiel" "$DB_FILE"

# Un dump vide ou tronqué pèse quelques centaines d'octets : on le refuse plutôt que de
# croire pendant des mois qu'on a des sauvegardes.
DB_SIZE=$(stat -c%s "$DB_FILE")
[ "$DB_SIZE" -gt 1024 ] || fail "dump suspect ($DB_SIZE octets)"
log "base sauvegardée ($(numfmt --to=iec "$DB_SIZE"))"

# -------------------------------------------------------------------- CV

CV_FILE="$BACKUP_DIR/cvs-$DATE.tar.gz"
log "sauvegarde des CV vers $CV_FILE"

docker run --rm \
  -v "$CV_VOLUME":/data:ro \
  -v "$BACKUP_DIR":/backup \
  alpine tar czf "/backup/$(basename "$CV_FILE").partiel" -C /data . \
  || fail "archive des CV impossible"

mv "$CV_FILE.partiel" "$CV_FILE"
log "CV sauvegardés ($(numfmt --to=iec "$(stat -c%s "$CV_FILE")"))"

# -------------------------------------------------------------------- rotation

log "suppression des sauvegardes de plus de $RETENTION_DAYS jours"
find "$BACKUP_DIR" -name 'base-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -name 'cvs-*.tar.gz'  -mtime "+$RETENTION_DAYS" -delete

# Nettoie les restes d'une exécution interrompue.
find "$BACKUP_DIR" -name '*.partiel' -mtime +1 -delete

log "terminé — $(find "$BACKUP_DIR" -name 'base-*.sql.gz' | wc -l) sauvegarde(s) conservée(s)"

# -------------------------------------------------------------------- à ne pas oublier
#
# Une sauvegarde qui reste sur la même machine que la base ne protège de rien : si le disque
# ou le serveur tombe, les deux partent ensemble. Ajouter une copie hors du serveur, par
# exemple vers un Object Storage :
#
#   rclone copy "$BACKUP_DIR" remote:skulvi-backups --max-age 25h
#
# Et surtout : tester une restauration complète au moins une fois. Une sauvegarde jamais
# restaurée est une hypothèse, pas une garantie.
