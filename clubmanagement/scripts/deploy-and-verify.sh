#!/usr/bin/env bash
# Deploy jar S-Club lên VPS api.sclub.io.vn và kiểm tra nhanh sau khi khởi động lại.
# Chạy bằng Git Bash trên Windows.
#
#   ./scripts/deploy-and-verify.sh                # build (có test) + deploy + kiểm tra
#   ./scripts/deploy-and-verify.sh --skip-tests   # bỏ qua unit test khi build
#   ./scripts/deploy-and-verify.sh --skip-build   # dùng jar đang có trong target/
#   ./scripts/deploy-and-verify.sh --skip-deploy   # chỉ kiểm tra server, không upload
#
# JWT admin: đặt biến môi trường SCLUB_JWT trước khi chạy,
# script sẽ hỏi bàn phím (ẩn) nếu chưa có. Không truyền token qua tham số dòng lệnh.

set -euo pipefail

SSH_TARGET="root@14.225.206.142"
API_BASE="https://api.sclub.io.vn"
REMOTE_DIR="/var/www/clubmanagement"
SERVICE="clubmanagement"
JAR="target/clubmanagement-0.0.1-SNAPSHOT.jar"
HEALTH_URL="${API_BASE}/api/packages"
STARTUP_TIMEOUT=210

BUILD=1
RUN_TESTS=1
DEPLOY=1
RUN_ADMIN_CHECK=1
for arg in "$@"; do
    case "$arg" in
        --skip-tests)  RUN_TESTS=0 ;;
        --skip-build)  BUILD=0 ;;
        --skip-deploy) DEPLOY=0 ;;
        --skip-admin)  RUN_ADMIN_CHECK=0 ;;
        *) echo "Tham số không hỗ trợ: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")/.."

GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$BOLD" "$1" "$OFF"; }
ok()   { printf '%s  OK   %s\n' "$GREEN" "$1"; }
bad()  { printf '%s  FAIL %s\n' "$RED" "$1"; }
warn() { printf '%s  SKIP %s\n' "$YELLOW" "$1"; }
fail() { bad "$1"; exit 1; }

if ! command -v ssh >/dev/null 2>&1; then fail "Không tìm thấy ssh trong PATH"; fi

# ---------------------------------------------------------------- 1. Build
if [ "$BUILD" = "1" ]; then
    step "Build jar"
    if [ "$RUN_TESTS" = "1" ]; then
        ./mvnw -q -DskipTests=false package || fail "Build thất bại (test lỗi)"
    else
        ./mvnw -q -DskipTests package || fail "Build thất bại"
    fi
    [ -f "$JAR" ] || fail "Không tìm thấy $JAR"
    ok "$(basename "$JAR") - $(wc -c < "$JAR") bytes, sửa đổi lúc $(date -r "$JAR" '+%H:%M:%S')"
elif [ "$DEPLOY" = "1" ]; then
    step "Dùng jar có sẵn"
    [ -f "$JAR" ] || fail "Không tìm thấy $JAR"
    warn "Bỏ qua build: jar sửa đổi lúc $(date -r "$JAR" '+%Y-%m-%d %H:%M:%S') - chắc chắn là bản mới nhất?"
fi

# ------------------------------------------------------------- 2. Deploy
if [ "$DEPLOY" = "1" ]; then
    step "Backup jar đang chạy trên VPS"
    ssh "$SSH_TARGET" "cp -f ${REMOTE_DIR}/app.jar ${REMOTE_DIR}/app.jar.prev 2>/dev/null || true"
    ok "Đã lưu ${REMOTE_DIR}/app.jar.prev (rollback: cp app.jar.prev app.jar && systemctl restart ${SERVICE})"

    step "Upload jar"
    scp -q "$JAR" "${SSH_TARGET}:${REMOTE_DIR}/app.jar.new" || fail "Upload thất bại"
    ok "Đã tải lên ${REMOTE_DIR}/app.jar.new"

    step "Khởi động lại dịch vụ"
    ssh "$SSH_TARGET" "mv ${REMOTE_DIR}/app.jar.new ${REMOTE_DIR}/app.jar && systemctl restart ${SERVICE}"
    ok "systemctl restart ${SERVICE}"

    step "Chờ app dậy (tối đa ${STARTUP_TIMEOUT}s)"
    deadline=$((SECONDS + STARTUP_TIMEOUT))
    code="000"
    while [ $SECONDS -lt $deadline ]; do
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$HEALTH_URL" || echo "000")
        if [ "$code" = "200" ]; then break; fi
        if ! ssh "$SSH_TARGET" "systemctl is-active ${SERVICE} >/dev/null"; then
            ssh "$SSH_TARGET" "journalctl -u ${SERVICE} --since '-3 min' --no-pager | grep -E 'APPLICATION FAILED|Caused by|ERROR' | tail -25" || true
            fail "Dịch vụ ${SERVICE} không chạy"
        fi
        printf '.'
        sleep 5
    done
    echo
    [ "$code" = "200" ] || fail "${HEALTH_URL} trả ${code} sau ${STARTUP_TIMEOUT}s"
    ok "API trả lời 200"
else
    step "Bỏ qua deploy (--skip-deploy)"
fi

# --------------------------------------------------- 3. Flyway migration
step "Kiểm tra migration tracking_event"
MIG_LOG=$(ssh "$SSH_TARGET" "journalctl -u ${SERVICE} --since '-25 min' --no-pager | grep -Ei 'Migrating schema|Successfully applied|tracking_event' | tail -10" || true)
if [ -n "$MIG_LOG" ]; then
    echo "$MIG_LOG" | sed 's/^/       /'
    ok "Bảng tracking_event đã sẵn sàng"
else
    warn "Không thấy log migration (bản trước đó đã apply sẵn là bình thường)"
fi

ERR_LOG=$(ssh "$SSH_TARGET" "journalctl -u ${SERVICE} --since '-5 min' --no-pager | grep -E 'ERROR|Exception' | grep -v 'Chưa cấu hình' | tail -10" || true)
if [ -n "$ERR_LOG" ]; then
    printf '%s  Cảnh báo: log có lỗi trong 5 phút gần nhất:%s\n' "$YELLOW" "$OFF"
    echo "$ERR_LOG" | sed 's/^/       /'
fi

# ------------------------------------------------------- 4. Smoke ingest
step "Gửi 2 sự kiện test từ frontend (page_view, pricing_view)"
SESSION="deploy-$(date +%Y%m%d-%H%M%S)"
for EVENT in page_view pricing_view; do
    BODY=$(printf '{"eventName":"%s","sessionId":"%s","path":"/","utmSource":"facebook","utmMedium":"cpc","utmCampaign":"deploy-check"}' "$EVENT" "$SESSION")
    RESP=$(curl -s -w '\n%{http_code}' -X POST "${API_BASE}/api/analytics/events" \
        -H 'Content-Type: application/json' -d "$BODY")
    CODE=$(printf '%s' "$RESP" | tail -n1)
    if [ "$CODE" = "200" ]; then
        ok "${EVENT} -> 200"
    else
        printf '%s' "$RESP" | head -n -1 | sed 's/^/       /'
        bad "${EVENT} -> ${CODE}"
    fi
done

REJECT=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${API_BASE}/api/analytics/events" \
    -H 'Content-Type: application/json' \
    -d '{"eventName":"BAD EVENT!","sessionId":"'"$SESSION"'"}')
if [ "$REJECT" = "400" ]; then
    ok "Sự kiện tên không hợp lệ bị chặn -> 400"
else
    bad "Sự kiện tên không hợp lệ trả ${REJECT} (mong đợi 400)"
fi

# ------------------------------------------------------ 5. Admin report
if [ "$RUN_ADMIN_CHECK" = "1" ]; then
    step "Đọc báo cáo phễu + kênh UTM (cần JWT admin)"
    if [ -z "${SCLUB_JWT:-}" ]; then
        read -rs -p "  Dán JWT admin (ẩn, Enter để bỏ qua): " SCLUB_JWT
        echo
    fi
    if [ -z "${SCLUB_JWT:-}" ]; then
        warn "Không có JWT - bỏ qua bước kiểm tra /api/admin/analytics"
    else
        ADMIN_JSON=$(curl -s -H "Authorization: Bearer ${SCLUB_JWT}" "${API_BASE}/api/admin/analytics?days=30")
        unset SCLUB_JWT
        if command -v python3 >/dev/null 2>&1; then
            printf '%s' "$ADMIN_JSON" | python3 -m json.tool || true
        else
            printf '%s\n' "$ADMIN_JSON"
        fi
    fi
else
    step "Bỏ qua báo cáo admin (--skip-admin)"
fi

step "Hoàn tất"
echo "  session test: ${SESSION}"
echo "  Rollback nếu cần:"
echo "    ssh ${SSH_TARGET} \"cp ${REMOTE_DIR}/app.jar.prev ${REMOTE_DIR}/app.jar && systemctl restart ${SERVICE}\""
