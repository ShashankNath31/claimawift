import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ClaimAuditEvent,
  ClaimBankDetailsRequest,
  ClaimBankDetailsResponse,
  ClaimCreateRequest,
  ClaimResponse,
  ClaimStatusUpdateRequest
} from '../models/claim.models';
import { PolicyPortfolioResponse } from '../models/policy.models';
import { PaginatedResponse, StandardResponse } from '../models/common.models';

@Injectable({
  providedIn: 'root'
})
export class ClaimsApiService {
  private readonly baseUrl = '/api/claims';

  constructor(private readonly http: HttpClient) {}

  createClaim(payload: ClaimCreateRequest): Observable<StandardResponse<ClaimResponse>> {
    return this.http.post<StandardResponse<ClaimResponse>>(this.baseUrl, payload);
  }

  getMyClaims(): Observable<StandardResponse<ClaimResponse[]>> {
    return this.http.get<StandardResponse<ClaimResponse[]>>(`${this.baseUrl}/my-claims`);
  }

  getAssignedClaims(): Observable<StandardResponse<ClaimResponse[]>> {
    return this.http.get<StandardResponse<ClaimResponse[]>>(`${this.baseUrl}/assigned`);
  }

  getClaimHistory(): Observable<StandardResponse<ClaimResponse[]>> {
    return this.http.get<StandardResponse<ClaimResponse[]>>(`${this.baseUrl}/history`);
  }

  getMyPolicies(): Observable<StandardResponse<PolicyPortfolioResponse>> {
    return this.http.get<StandardResponse<PolicyPortfolioResponse>>(`${this.baseUrl}/policies/my`);
  }

  getClaimById(claimId: number): Observable<StandardResponse<ClaimResponse>> {
    return this.http.get<StandardResponse<ClaimResponse>>(`${this.baseUrl}/${claimId}`);
  }

  getClaimByNumber(claimNumber: string): Observable<StandardResponse<ClaimResponse>> {
    return this.http.get<StandardResponse<ClaimResponse>>(
      `${this.baseUrl}/number/${encodeURIComponent(claimNumber)}`
    );
  }

  getClaims(page = 0, size = 10, status?: string): Observable<StandardResponse<PaginatedResponse<ClaimResponse>>> {
    const statusQuery = status ? `&status=${encodeURIComponent(status)}` : '';
    return this.http.get<StandardResponse<PaginatedResponse<ClaimResponse>>>(
      `${this.baseUrl}?page=${page}&size=${size}${statusQuery}`
    );
  }

  searchMyClaims(query: string): Observable<StandardResponse<ClaimResponse[]>> {
    return this.http.get<StandardResponse<ClaimResponse[]>>(
      `${this.baseUrl}/my-claims/search?query=${encodeURIComponent(query)}`
    );
  }

  searchClaims(query: string): Observable<StandardResponse<ClaimResponse[]>> {
    return this.http.get<StandardResponse<ClaimResponse[]>>(
      `${this.baseUrl}/search?query=${encodeURIComponent(query)}`
    );
  }

  updateClaimStatus(
    claimId: number,
    payload: ClaimStatusUpdateRequest
  ): Observable<StandardResponse<ClaimResponse>> {
    return this.http.patch<StandardResponse<ClaimResponse>>(`${this.baseUrl}/${claimId}/status`, payload);
  }

  assignClaim(claimId: number, adjusterId: number): Observable<StandardResponse<ClaimResponse>> {
    return this.http.patch<StandardResponse<ClaimResponse>>(`${this.baseUrl}/${claimId}/assign`, { adjusterId });
  }

  unassignClaim(claimId: number): Observable<StandardResponse<ClaimResponse>> {
    return this.http.patch<StandardResponse<ClaimResponse>>(`${this.baseUrl}/${claimId}/unassign`, {});
  }

  getClaimAudit(claimId: number): Observable<StandardResponse<ClaimAuditEvent[]>> {
    return this.http.get<StandardResponse<ClaimAuditEvent[]>>(`${this.baseUrl}/${claimId}/audit`);
  }

  updateClaimBankDetails(
    claimId: number,
    payload: ClaimBankDetailsRequest
  ): Observable<StandardResponse<ClaimBankDetailsResponse>> {
    return this.http.put<StandardResponse<ClaimBankDetailsResponse>>(`${this.baseUrl}/${claimId}/bank-details`, payload);
  }

  getClaimBankDetails(claimId: number): Observable<StandardResponse<ClaimBankDetailsResponse>> {
    return this.http.get<StandardResponse<ClaimBankDetailsResponse>>(`${this.baseUrl}/${claimId}/bank-details`);
  }
}
