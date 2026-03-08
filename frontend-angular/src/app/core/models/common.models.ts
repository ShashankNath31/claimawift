export interface StandardResponse<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
