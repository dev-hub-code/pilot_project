Key pairs used **only** by the automated test suite. They have no value outside tests and must
never be configured in any deployed environment. `test-only-other-public.pem` belongs to a
different key pair and is used to prove that foreign signatures are rejected.
