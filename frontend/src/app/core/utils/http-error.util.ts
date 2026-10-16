import { HttpErrorResponse } from '@angular/common/http';

/** True only when the backend explicitly rejected the credentials (401/403). */
export function isAuthRejection(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
}
