#!/bin/sh
# Genere au premier demarrage le certificat auto-signe du mode remote (HTTPS du kiosque), puis lance l'application.
set -eu

CERT_DIR="${ASSISTANT_HTTPS_CERT_DIR:-/certs}"
KEYSTORE="${ASSISTANT_HTTPS_KEYSTORE_PATH:-$CERT_DIR/assistant.p12}"
PASS_FILE="$CERT_DIR/assistant.pass"
GENERATED_ALIAS="assistant"

https_wanted() {
    case ",${SPRING_PROFILES_ACTIVE:-}," in
        *,remote,*|*,prod,*|*,https-dual,*) return 0 ;;
    esac
    [ "${ASSISTANT_HTTPS_ENABLED:-false}" = "true" ]
}

warn() { echo "[entrypoint] $*" >&2; }
fail() { warn "ERREUR : $*"; exit 1; }

# IPv4 et IPv6 en ip:, tout le reste en dns:.
subject_alt_name() {
    san="dns:localhost,ip:127.0.0.1"
    old_ifs="$IFS"; IFS=','
    for raw in ${ASSISTANT_HTTPS_HOSTNAMES:-}; do
        host=$(printf '%s' "$raw" | tr -d '[]')
        printf '%s' "$host" | grep -Eq '^[A-Za-z0-9.:-]+$' || fail "ASSISTANT_HTTPS_HOSTNAMES contient une valeur invalide : $raw"
        case "$host" in
            *:*) san="$san,ip:$host" ;;
            *) if printf '%s' "$host" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then san="$san,ip:$host"; else san="$san,dns:$host"; fi ;;
        esac
    done
    IFS="$old_ifs"
    printf '%s' "$san"
}

generate_keystore() {
    mkdir -p "$CERT_DIR" 2>/dev/null || true
    [ -w "$CERT_DIR" ] || fail "$CERT_DIR n'est pas inscriptible (volume des certificats)."
    if [ -z "${ASSISTANT_HTTPS_KEYSTORE_PASSWORD:-}" ]; then
        if [ ! -s "$PASS_FILE" ]; then
            ( umask 077; head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n' > "$PASS_FILE" ) || fail "ecriture impossible dans $CERT_DIR"
        fi
        ASSISTANT_HTTPS_KEYSTORE_PASSWORD=$(cat "$PASS_FILE")
        export ASSISTANT_HTTPS_KEYSTORE_PASSWORD
    fi
    if [ -s "$KEYSTORE" ]; then
        return 0
    fi
    if ! grep -q " $CERT_DIR " /proc/mounts 2>/dev/null; then
        warn "AVERTISSEMENT : $CERT_DIR n'est pas un volume, le certificat sera regenere a chaque demarrage."
    fi
    SAN=$(subject_alt_name) || exit 1
    KEYTOOL_PASSWORD="$ASSISTANT_HTTPS_KEYSTORE_PASSWORD" keytool -genkeypair -alias "$GENERATED_ALIAS" \
        -keyalg RSA -keysize 2048 -validity "${ASSISTANT_HTTPS_VALIDITY_DAYS:-825}" \
        -dname "CN=assistant" -ext "SAN=$SAN" \
        -storetype PKCS12 -keystore "$KEYSTORE" -storepass:env KEYTOOL_PASSWORD -noprompt >/dev/null \
        || fail "generation du certificat impossible."
    chmod 600 "$KEYSTORE"
    warn "Certificat auto-signe genere : $KEYSTORE (noms : $SAN)."
}

if https_wanted; then
    if [ -n "${ASSISTANT_HTTPS_KEYSTORE_PATH:-}" ]; then
        [ -r "$ASSISTANT_HTTPS_KEYSTORE_PATH" ] || fail "keystore fourni illisible : $ASSISTANT_HTTPS_KEYSTORE_PATH"
        [ -n "${ASSISTANT_HTTPS_KEYSTORE_PASSWORD:-}" ] || fail "ASSISTANT_HTTPS_KEYSTORE_PASSWORD est obligatoire avec un keystore fourni."
    else
        generate_keystore
    fi
fi

exec java -jar /app/app.jar "$@"
