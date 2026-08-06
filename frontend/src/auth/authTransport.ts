export interface AuthTransport {
  request<T>(path: string, options?: RequestInit): Promise<T>;
  optionallyAuthenticatedRequest<T>(path: string, options?: RequestInit): Promise<T>;
}
