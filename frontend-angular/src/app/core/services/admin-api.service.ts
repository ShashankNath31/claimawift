import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthResponse, UserProfile } from '../models/auth.models';
import { StandardResponse } from '../models/common.models';
import { AdminCreateUserRequest, RoleUpdateRequest, StatusUpdateRequest } from '../models/admin.models';

@Injectable({
  providedIn: 'root'
})
export class AdminApiService {
  private readonly baseUrl = '/api/auth';

  constructor(private readonly http: HttpClient) {}

  getMe(): Observable<StandardResponse<UserProfile>> {
    return this.http.get<StandardResponse<UserProfile>>(`${this.baseUrl}/me`);
  }

  getAdjusters(): Observable<StandardResponse<UserProfile[]>> {
    return this.http.get<StandardResponse<UserProfile[]>>(`${this.baseUrl}/adjusters`);
  }

  getUsers(): Observable<StandardResponse<UserProfile[]>> {
    return this.http.get<StandardResponse<UserProfile[]>>(`${this.baseUrl}/admin/users`);
  }

  getUserById(userId: number): Observable<StandardResponse<UserProfile>> {
    return this.http.get<StandardResponse<UserProfile>>(`${this.baseUrl}/admin/users/${userId}`);
  }

  createInternalUser(payload: AdminCreateUserRequest): Observable<StandardResponse<UserProfile>> {
    return this.http.post<StandardResponse<UserProfile>>(`${this.baseUrl}/admin/users`, payload);
  }

  updateUserRoles(userId: number, payload: RoleUpdateRequest): Observable<StandardResponse<UserProfile>> {
    return this.http.put<StandardResponse<UserProfile>>(`${this.baseUrl}/admin/users/${userId}/roles`, payload);
  }

  updateUserStatus(userId: number, payload: StatusUpdateRequest): Observable<StandardResponse<UserProfile>> {
    return this.http.patch<StandardResponse<UserProfile>>(`${this.baseUrl}/admin/users/${userId}/status`, payload);
  }

  refreshToken(): Observable<StandardResponse<AuthResponse>> {
    return this.http.post<StandardResponse<AuthResponse>>(`${this.baseUrl}/refresh`, {});
  }
}
