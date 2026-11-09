import { redirect } from "next/navigation";

/** Employees are created under Staff; this keeps old links working. */
export default function OldNewEmployeePage() {
  redirect("/platform/staff/new");
}
