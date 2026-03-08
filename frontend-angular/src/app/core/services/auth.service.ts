import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';
import {
  AuthPayload,
  AuthResponse,
  LoginChallengeResponse,
  RegisterPayload,
  RoleName,
  UserProfile,
  VerifyOtpPayload
} from '../models/auth.models';
import { StandardResponse } from '../models/common.models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiBaseUrl = '/api/auth';
  private readonly tokenStorageKey = 'claimswift_access_token';
  private readonly userStorageKey = 'claimswift_user';
  private readonly currentUserSubject = new BehaviorSubject<UserProfile | null>(this.loadUser());

  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(private readonly http: HttpClient) {
    if (!this.isTokenValid(this.getAccessToken())) {
      this.clearStorage();
    }
  }

  login(payload: AuthPayload): Observable<StandardResponse<LoginChallengeResponse>> {
    return this.http.post<StandardResponse<LoginChallengeResponse>>(`${this.apiBaseUrl}/login`, payload);
  }

  verifyLoginOtp(payload: VerifyOtpPayload): Observable<StandardResponse<AuthResponse>> {
    return this.http
      .post<StandardResponse<AuthResponse>>(`${this.apiBaseUrl}/login/verify-otp`, payload)
      .pipe(tap((response) => this.handleAuthSuccess(response.data)));
  }

  register(payload: RegisterPayload): Observable<StandardResponse<AuthResponse>> {
    return this.http
      .post<StandardResponse<AuthResponse>>(`${this.apiBaseUrl}/register`, payload)
      .pipe(tap((response) => this.handleAuthSuccess(response.data)));
  }

  logout(): Observable<StandardResponse<null> | null> {
    return this.http.post<StandardResponse<null>>(`${this.apiBaseUrl}/logout`, {}).pipe(
      catchError(() => of(null)),
      tap(() => this.clearStorage())
    );
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.tokenStorageKey);
  }

  isAuthenticated(): boolean {
    return this.isTokenValid(this.getAccessToken());
  }

  getCurrentUserSnapshot(): UserProfile | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: RoleName): boolean {
    const user = this.currentUserSubject.value;
    return !!user?.roles?.includes(role);
  }

  hasAnyRole(roles: RoleName[]): boolean {
    return roles.some((role) => this.hasRole(role));
  }

  private handleAuthSuccess(response: AuthResponse): void {
    localStorage.setItem(this.tokenStorageKey, response.accessToken);
    localStorage.setItem(this.userStorageKey, JSON.stringify(response.user));
    this.currentUserSubject.next(response.user);
  }

  private loadUser(): UserProfile | null {
    const userJson = localStorage.getItem(this.userStorageKey);
    if (!userJson) {
      return null;
    }
    try {
      return JSON.parse(userJson) as UserProfile;
    } catch {
      localStorage.removeItem(this.userStorageKey);
      return null;
    }
  }

  private isTokenValid(token: string | null): boolean {
    if (!token) {
      return false;
    }

    const payload = this.decodeJwt(token);
    if (!payload || typeof payload.exp !== 'number') {
      return false;
    }

    const nowInSeconds = Math.floor(Date.now() / 1000);
    return payload.exp > nowInSeconds;
  }

  private decodeJwt(token: string): { exp?: number } | null {
    const segments = token.split('.');
    if (segments.length !== 3) {
      return null;
    }

    let payload = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    while (payload.length % 4 !== 0) {
      payload += '=';
    }
    try {
      return JSON.parse(atob(payload)) as { exp?: number };
    } catch {
      return null;
    }
  }

  private clearStorage(): void {
    localStorage.removeItem(this.tokenStorageKey);
    localStorage.removeItem(this.userStorageKey);
    this.currentUserSubject.next(null);
  }
}
