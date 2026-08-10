#!/usr/bin/env bash
set -Eeuo pipefail

TOKEN_CODE="${1:?사용법: $0 <MFA_코드> [duration-seconds]}"
DURATION_SECONDS="${2:-129600}"
SOURCE_PROFILE="${AWS_SOURCE_PROFILE:-mydev}"
TARGET_PROFILE="${AWS_MFA_PROFILE:-mydev-mfa}"
MFA_SERIAL="${AWS_MFA_SERIAL:-arn:aws:iam::869652444193:mfa/softeer17}"

read -r ACCESS_KEY_ID SECRET_ACCESS_KEY SESSION_TOKEN <<< "$(
  aws sts get-session-token \
    --serial-number "$MFA_SERIAL" \
    --token-code "$TOKEN_CODE" \
    --duration-seconds "$DURATION_SECONDS" \
    --profile "$SOURCE_PROFILE" \
    --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
    --output text
)"

aws configure set aws_access_key_id "$ACCESS_KEY_ID" --profile "$TARGET_PROFILE"
aws configure set aws_secret_access_key "$SECRET_ACCESS_KEY" --profile "$TARGET_PROFILE"
aws configure set aws_session_token "$SESSION_TOKEN" --profile "$TARGET_PROFILE"

echo "[mfa] ${TARGET_PROFILE} 프로필 세션 자격증명을 갱신했습니다 (유효기간 ${DURATION_SECONDS}초)."
