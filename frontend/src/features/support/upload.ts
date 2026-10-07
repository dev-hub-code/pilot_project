import "server-only";
import { MAX_FILE_BYTES } from "@/validators/profile";
import { MAX_ATTACHMENTS } from "@/validators/support";

/** Copies the chosen files into the outgoing multipart body; returns an error message instead when they don't fit. */
export function appendAttachments(from: FormData, to: FormData): string | null {
  const files = from.getAll("files").filter((f): f is File => f instanceof File && f.size > 0);
  if (files.length > MAX_ATTACHMENTS) return `Attach at most ${MAX_ATTACHMENTS} files`;
  for (const file of files) {
    if (file.size > MAX_FILE_BYTES) return "Files must be 5 MB or smaller";
    to.append("files", file, file.name);
  }
  return null;
}
