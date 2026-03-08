import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();
  const requestPath = resolveRequestPath(req.url);
  const isApiRequest = requestPath.startsWith('/api/');

  if (!token) {
    return next(req);
  }

  if (isPublicAuthPath(requestPath)) {
    return next(req);
  }

  if (!isApiRequest) {
    return next(req);
  }

  if (req.headers.has('Authorization')) {
    return next(req);
  }

  const authenticatedRequest = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });

  return next(authenticatedRequest);
};

function resolveRequestPath(url: string): string {
  if (!url) {
    return '';
  }

  if (url.startsWith('/')) {
    return url;
  }

  try {
    return new URL(url, window.location.origin).pathname;
  } catch {
    return url;
  }
}

function isPublicAuthPath(path: string): boolean {
  return (
    path === '/api/auth/login' ||
    path === '/api/auth/login/verify-otp' ||
    path === '/api/auth/register'
  );
}
