/** Mirrors the backend's PlanPhotoService.MAX_PHOTOS. */
export const MAX_PLAN_PHOTOS = 10;

/** Per photo, as the backend accepts. */
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

/** One upload request carries at most this much (the server action body limit is 22 MB). */
export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

export const PHOTO_TYPES = "image/jpeg,image/png";

/** The photos chosen in a form's file input (empty file inputs submit one empty file). */
export function chosenPhotos(formData: FormData, name = "photos"): File[] {
  return formData.getAll(name).filter((f): f is File => f instanceof File && f.size > 0);
}

/** Why these photos cannot be uploaded together, or null. */
export function photoProblem(files: File[], room: number): string | null {
  if (files.length > room) return room === 0 ? "This plan already has the most photos it can have" : `Choose at most ${room} more photo${room === 1 ? "" : "s"}`;
  const tooBig = files.find((f) => f.size > MAX_PHOTO_BYTES);
  if (tooBig) return `${tooBig.name} is over 5 MB`;
  const wrongType = files.find((f) => !["image/jpeg", "image/png"].includes(f.type));
  if (wrongType) return `${wrongType.name} is not a JPEG or PNG image`;
  if (files.reduce((sum, f) => sum + f.size, 0) > MAX_UPLOAD_BYTES) return "Upload at most 20 MB at a time; choose fewer photos";
  return null;
}
