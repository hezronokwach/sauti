/** One invalid field, as listed in an ApiError. */
export interface ApiFieldError {
  field: string;
  message: string;
}

/** The error shape every backend endpoint returns (see the backend's ApiError record). */
export interface ApiError {
  code: string;
  message: string;
  fieldErrors: ApiFieldError[];
  requestId: string | null;
}
