#!/usr/bin/env bash
# 사용법: ./run.sh rate|error|latency
set -euo pipefail
base_url="${BASE_URL:-http://sns.localhost}"
duration="${DURATION_SECONDS:-180}"
recovery="${RECOVERY_SECONDS:-180}"

# 인자가 rate 인 경우 -> 호출 API: /api/v1/demo/ok, 기대 HTTP 상태: 200, 요청 간격: 0.05초, 목적: 요청량 급증 경보
# 인자가 error 인 경우 -> 호출 API: /api/v1/demo/error, 기대 HTTP 상태: 500, 요청 간격: 1초, 목적: 에러율 경보
# 인자가 slow 인 경우 -> 호출 API: /api/v1/demo/slow, 기대 HTTP 상태: 200, 요청 간격: 1초, 목적: 응답 지연 경보
case "${1:-}" in
    rate) path=ok; expected=200; pause=0.05 ;;
    error) path=error; expected=500; pause=1 ;;
    latency) path=slow; expected=200; pause=1 ;;
    *) echo "사용법: $0 rate|error|latency" >&2; exit 2 ;;
esac
# duration과 recovery가 올바른 숫자인지 검사
# ^[1-9]: 첫 글자는 1~9 (0이나 음수, 문자는 불가)
# [0-9]*$: 뒤에는 숫자가 0개 이상
for seconds in "$duration" "$recovery"; do
    if ! [[ "$seconds" =~ ^[1-9][0-9]*$ ]]; then
        echo "실행 시간은 양의 정수(초)로 입력하세요." >&2
        exit 2
    fi
done

# -s	진행 상황 표시 숨김 (silent)
# -S	-s와 함께 쓰면 에러는 표시
# -o /dev/null	응답 본문은 버림
# -w '%{http_code}'	응답 본문 대신 HTTP 상태 코드만 출력
# --max-time 10	10초 넘으면 포기
request() {
    local status
    status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "$base_url/api/v1/demo/$1")
    if [ "$status" != "$2" ]; then
        echo "예상 HTTP $2, 실제 $status: $1 (08강 앱 배포를 확인하세요.)" >&2
        exit 1
    fi
}

# 일정 시간동안 반복 요청
# 호출 방식: traffic <지속시간(초)> <경로> <기대상태> <요청간격(초)>
# SECONDS: bash가 자동으로 관리하는 스크립트 시작 후 경과 시간(초) 변수
# end: "지금 + 지속시간" = 종료 시각
# while 루프: 종료 시각이 될 때까지 요청 → 대기 → 요청 → 대기를 반복
traffic() {
    local end=$((SECONDS + $1))
    while (( SECONDS < end )); do
        request "$2" "$3"
        sleep "$4"
    done
}

trap 'echo "요청을 중단했습니다. 정상 요청으로 복구를 확인하세요."; exit 130' INT TERM
# 사전 점검: 본격 시작 전에 대상 API가 정상 응답하는지 1번 호출 (서버가 죽어 있으면 여기서 바로 실패)
request "$path" "$expected"
echo "==> 정상 요청으로 30초 준비합니다"
# 30초 동안, /ok를 호출하고 200을 기대하며, 1초마다 반복
traffic 30 ok 200 1
# 예: rate 요청을 180 초 보냅니다.
echo "==> $1 요청을 ${duration}초 보냅니다. Prometheus Alerts와 Slack을 확인하세요."
traffic "$duration" "$path" "$expected" "$pause"
echo "==> 정상 요청으로 ${recovery}초 복구합니다"
traffic "$recovery" ok 200 1
echo "완료. 경보가 inactive로 돌아왔는지 확인하세요. Slack 해소 알림은 조금 더 걸릴 수 있어요."
