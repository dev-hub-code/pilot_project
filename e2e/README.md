# Role access tests

Signs in as a real employee of every role, calls every admin endpoint and opens every admin page, and compares what
happens with what the role's permissions say. `scenarios/<ROLE>.md` holds the expectation and result for each role, and
`RESULTS.md` the summary.

The tests create accounts and data, so run them only against a throwaway database:

```sh
docker exec -i sealease-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE sealease_e2e"'
# Backend on that database (stop any other backend first; port 8080 and the Kafka topics are shared)
(cd backend && DB_URL=jdbc:postgresql://localhost:5433/sealease_e2e APP_NOTIFICATION_EMAIL_ENABLED=false ./mvnw -q spring-boot:run)
# Production frontend on port 3001
(cd frontend && npx next build && COOKIE_SECURE=false npx next start -p 3001)

python3 e2e/build_catalog.py
set -a; . ./.env; set +a
E2E_DATABASE=sealease_e2e E2E_CHROME=/path/to/chrome NODE_PATH=/path/with/playwright-core/node_modules node e2e/run.cjs
python3 e2e/report.py
```

`E2E_ONLY=SUPER_ADMIN,FINANCE` limits the run to some roles. `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` sign
in the first admin; every other account and password is generated for the run.

**How a check is judged.** The expected answer comes from the endpoint's `@PreAuthorize` rule and the role's permissions.
Allowed means the request got past authorization (a 404 for the made-up ID still counts); refused means 403. Every call
uses a made-up ID, so a permitted call acts on nothing. `build_catalog.py` needs a valid sample body for each write,
because Spring validates a body before it checks permission, and a refused role would otherwise get 400.

These tests show that each role gets what its permissions say. Whether a role *should* hold those permissions is a
business decision: review the Permissions section of each scenario for that.
