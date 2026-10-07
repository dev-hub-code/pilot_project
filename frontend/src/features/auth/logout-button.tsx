import { Button } from "@/components/ui/button";
import { logoutAction } from "./actions";

export function LogoutButton() {
  return (
    <form action={logoutAction}>
      <Button type="submit" variant="secondary" className="h-10 whitespace-nowrap px-4">
        Sign out
      </Button>
    </form>
  );
}
