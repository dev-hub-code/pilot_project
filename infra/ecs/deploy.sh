#!/usr/bin/env bash
# Deploys main to production by hand (there is no CI/CD): runs the checks, builds the backend and frontend images,
# pushes them to ECR tagged with the commit, and rolls the services onto them (infra/ecs/rollout.sh: API first, then
# web; fails if ECS rolls a service back). Needs AWS credentials, Docker, Java 25 and Node.
#   infra/ecs/deploy.sh                  check, build, push and roll out origin/main
#   infra/ecs/deploy.sh --skip-checks    the same without the tests (e.g. re-deploying a commit already checked)
#   infra/ecs/deploy.sh --push           build and push only (the first time, before the services exist)
#   infra/ecs/deploy.sh --any-branch     deploy HEAD even if it is not origin/main (hotfixes; use with care)
set -euo pipefail
cd "$(dirname "$0")/../.."

checks=true push_only=false any_branch=false
for arg in "$@"; do
  case "$arg" in
    --skip-checks) checks=false ;;
    --push) push_only=true ;;
    --any-branch) any_branch=true ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or stash your changes first: the images are tagged with the commit they are built from." >&2
  exit 1
fi
git fetch -q origin main
if [[ "$any_branch" == false && "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
  echo "HEAD is not origin/main. Run: git switch main && git pull --ff-only (or pass --any-branch)." >&2
  exit 1
fi
tag=$(git rev-parse --short=12 HEAD)
region=$(terraform -chdir=infra/ecs output -raw region 2>/dev/null || echo ap-south-1)
export AWS_REGION=$region
live=$(aws ssm get-parameter --name /sealease/image-tag --query Parameter.Value --output text 2>/dev/null || echo none)
echo "Deploying $tag ($(git log -1 --format=%s)); live now: $live"

# The checks CI would run: backend tests (Testcontainers needs Docker) and the frontend typecheck, lint and build.
if [[ "$checks" == true ]]; then
  (cd backend && ./mvnw -q -B verify)
  (cd frontend && npm ci --no-audit --no-fund && npm run typecheck && npm run lint && npm run build)
  (cd frontend && npm audit --omit=dev --audit-level=high)
fi

repos=$(terraform -chdir=infra/ecs output -json ecr_repositories)
registry=$(jq -r '.backend' <<<"$repos" | cut -d/ -f1)
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry"

for app in backend frontend; do
  repo=$(jq -r ".$app" <<<"$repos")
  if aws ecr describe-images --repository-name "${repo#*/}" --image-ids imageTag="$tag" >/dev/null 2>&1; then
    echo "$app:$tag is already in ECR"
    continue
  fi
  docker build --platform linux/amd64 --provenance=false -t "$repo:$tag" "$app"
  docker push "$repo:$tag"
done

if [[ "$push_only" == true ]]; then
  echo "Pushed $tag. Roll it out with: infra/ecs/rollout.sh $tag"
  exit 0
fi
infra/ecs/rollout.sh "$tag"
echo "Live: https://www.sealease.in (previous: $live; roll back with infra/ecs/rollout.sh $live)"
