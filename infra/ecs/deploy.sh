#!/usr/bin/env bash
# Builds the backend and frontend images, pushes them to ECR tagged with the current git commit, and rolls them out
# (terraform apply with that tag). Run from a clean checkout of what should go live, with AWS credentials set.
#   infra/ecs/deploy.sh            build, push and deploy HEAD
#   infra/ecs/deploy.sh --push     build and push only (the first time, before the services exist)
set -euo pipefail
cd "$(dirname "$0")/../.."

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or stash your changes first: the images are tagged with the commit they are built from." >&2
  exit 1
fi
tag=$(git rev-parse --short=12 HEAD)
region=$(terraform -chdir=infra/ecs output -raw region 2>/dev/null || echo ap-south-1)
repos=$(terraform -chdir=infra/ecs output -json ecr_repositories)
registry=$(jq -r '.backend' <<<"$repos" | cut -d/ -f1)

aws ecr get-login-password --region "$region" | docker login --username AWS --password-stdin "$registry"

for app in backend frontend; do
  repo=$(jq -r ".$app" <<<"$repos")
  if aws ecr describe-images --region "$region" --repository-name "${repo#*/}" --image-ids imageTag="$tag" >/dev/null 2>&1; then
    echo "$app:$tag is already in ECR"
    continue
  fi
  docker build --platform linux/amd64 -t "$repo:$tag" "$app"
  docker push "$repo:$tag"
done

if [[ "${1:-}" == "--push" ]]; then
  echo "Pushed $tag. Deploy it with: terraform -chdir=infra/ecs apply -var image_tag=$tag"
  exit 0
fi
terraform -chdir=infra/ecs apply -var "image_tag=$tag"
aws ecs wait services-stable --region "$region" --cluster sealease --services backend frontend
echo "Deployed $tag: $(terraform -chdir=infra/ecs output -raw godaddy_forwarding)"
