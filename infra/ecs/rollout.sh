#!/usr/bin/env bash
# Rolls the ECS services onto images already in ECR (tagged <tag>): registers a copy of each service's current task
# definition with the new image, updates the service and waits for it. The API goes first, so the web app never runs
# against an older API. Fails if ECS rolled a service back (its deployment circuit breaker). On success, records the
# tag in SSM, where Terraform reads the live tag from.
#   infra/ecs/rollout.sh <tag>        (used by GitHub Actions; needs AWS credentials)
set -euo pipefail
tag=${1:?usage: rollout.sh <image tag>}
region=${AWS_REGION:-ap-south-1}
cluster=sealease
aws() { command aws --region "$region" "$@"; }

for service in backend frontend; do
  current=$(aws ecs describe-task-definition --task-definition "$cluster-$service" --query taskDefinition)
  repo=$(jq -r '.containerDefinitions[0].image | sub(":[^:/]+$"; "")' <<<"$current")
  # Only the fields RegisterTaskDefinition accepts; the image and APP_VERSION move to the new tag.
  next=$(jq --arg image "$repo:$tag" --arg tag "$tag" '
    {family, taskRoleArn, executionRoleArn, networkMode, containerDefinitions, volumes, placementConstraints,
     requiresCompatibilities, cpu, memory, runtimePlatform}
    | with_entries(select(.value != null))
    | .containerDefinitions |= map(
        .image = $image
        | .environment = ((.environment // []) | map(if .name == "APP_VERSION" then .value = $tag else . end)))' \
    <<<"$current")
  arn=$(aws ecs register-task-definition --cli-input-json "$next" --query taskDefinition.taskDefinitionArn --output text)
  echo "$service: rolling out $arn"
  aws ecs update-service --cluster "$cluster" --service "$service" --task-definition "$arn" >/dev/null
  aws ecs wait services-stable --cluster "$cluster" --services "$service"
  running=$(aws ecs describe-services --cluster "$cluster" --services "$service" --query 'services[0].taskDefinition' --output text)
  if [[ "$running" != "$arn" ]]; then
    echo "$service: the new tasks did not become healthy; ECS rolled back to $running" >&2
    exit 1
  fi
done

aws ssm put-parameter --name "/$cluster/image-tag" --value "$tag" --overwrite >/dev/null
echo "Deployed $tag"
