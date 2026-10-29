#!/usr/bin/env bash
set -euo pipefail
: "${AWS_REGION:?}" "${ECR_REPOSITORY_URL:?}" "${ECS_CLUSTER:?}" "${ECS_SERVICE:?}"
: "${IMAGE_TAG:?}" "${API_HEALTH_URL:?}"
[[ "$API_HEALTH_URL" == https://* ]] || { echo 'Health URL must use HTTPS'; exit 1; }
registry="${ECR_REPOSITORY_URL%%/*}"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$registry"
docker buildx build --platform linux/amd64 --file deployment/Dockerfile \
  --tag "$ECR_REPOSITORY_URL:$IMAGE_TAG" --push .
# Resolve to an immutable digest and preserve the running configuration.
digest=$(aws ecr describe-images --repository-name "${ECR_REPOSITORY_URL#*/}" \
  --image-ids "imageTag=$IMAGE_TAG" --query 'imageDetails[0].imageDigest' --output text)
export DEPLOY_IMAGE="$ECR_REPOSITORY_URL@$digest"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
previous=$(aws ecs describe-services --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
  --query 'services[0].taskDefinition' --output text)
[[ "$previous" == arn:aws:ecs:* ]] || { echo 'Missing bootstrapped service'; exit 1; }
aws ecs describe-task-definition --task-definition "$previous" --query taskDefinition > "$work/current.json"
python3 - "$work/current.json" "$work/next.json" <<'PY'
import json, os, sys
with open(sys.argv[1]) as f: task = json.load(f)
allowed = {'family','taskRoleArn','executionRoleArn','networkMode','containerDefinitions',
 'volumes','placementConstraints','requiresCompatibilities','cpu','memory',
 'pidMode','ipcMode','proxyConfiguration','inferenceAccelerators','ephemeralStorage','runtimePlatform'}
task = {k:v for k,v in task.items() if k in allowed}
containers = [c for c in task['containerDefinitions'] if c['name'] == 'compira-api']
if len(containers) != 1: raise SystemExit('Expected exactly one compira-api container')
containers[0]['image'] = os.environ['DEPLOY_IMAGE']
flag = os.environ.get('NOTIFICATIONS_REALTIME_ENABLED', '')
if flag:
    if flag not in ('true', 'false'): raise SystemExit('Invalid realtime flag')
    env = {v['name']:v['value'] for v in containers[0].get('environment', [])}
    env['NOTIFICATIONS_REALTIME_ENABLED'] = flag
    if flag == 'true':
        for key in ('NOTIFICATIONS_REALTIME_URL', 'NOTIFICATIONS_REALTIME_TABLE', 'NOTIFICATIONS_REALTIME_QUEUE_URL'):
            value = os.environ.get(key, '')
            if not value: raise SystemExit('Missing realtime deployment setting: ' + key)
            env[key] = value
        if not env['NOTIFICATIONS_REALTIME_URL'].startswith('wss://'): raise SystemExit('WebSocket URL must use TLS')
    containers[0]['environment'] = [{'name':k,'value':v} for k,v in env.items()]
with open(sys.argv[2], 'w') as f: json.dump(task, f)
PY
next=$(aws ecs register-task-definition --cli-input-json "file://$work/next.json" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
printf 'Previous task: %s\nRequested task: %s\nImage: %s\n' "$previous" "$next" "$DEPLOY_IMAGE"
aws ecs update-service --cluster "$ECS_CLUSTER" --service "$ECS_SERVICE" \
  --task-definition "$next" > /dev/null
aws ecs wait services-stable --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE"
python3 scripts/deploy/verify_ecs_rollout.py --cluster "$ECS_CLUSTER" \
  --service "$ECS_SERVICE" --task-definition "$next"

curl --fail --silent --show-error --retry 8 --retry-all-errors --retry-delay 10 \
  --connect-timeout 10 --max-time 30 "$API_HEALTH_URL"
echo 'QA backend rollout and public health check passed.'
