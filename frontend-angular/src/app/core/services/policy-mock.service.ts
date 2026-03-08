import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';
import { PolicyPortfolioResponse, PolicyResponse } from '../models/policy.models';

@Injectable({
  providedIn: 'root'
})
export class PolicyMockService {
  private readonly mockPoliciesUrl = '/assets/mocks/policies.json';

  constructor(private readonly http: HttpClient) {}

  getFallbackPolicies(): Observable<PolicyResponse[]> {
    return this.http.get<PolicyResponse[]>(this.mockPoliciesUrl).pipe(
      map((policies) => this.normalizePolicies(policies)),
      catchError(() => of([]))
    );
  }

  getFallbackActivePolicies(): Observable<PolicyResponse[]> {
    return this.getFallbackPolicies().pipe(
      map((policies) => policies.filter((policy) => policy.status === 'ACTIVE'))
    );
  }

  getFallbackPortfolio(): Observable<PolicyPortfolioResponse> {
    return this.getFallbackPolicies().pipe(
      map((policies) => {
        const activePolicies = policies.filter((policy) => policy.status === 'ACTIVE').length;
        const expiredPolicies = policies.filter((policy) => policy.status === 'EXPIRED').length;
        return {
          totalPolicies: policies.length,
          activePolicies,
          expiredPolicies,
          policies
        };
      })
    );
  }

  private normalizePolicies(policies: PolicyResponse[] | null | undefined): PolicyResponse[] {
    if (!Array.isArray(policies)) {
      return [];
    }

    return policies
      .filter((policy) => !!policy?.policyNumber)
      .map((policy) => {
        const normalizedStatus = String(policy.status || '').toUpperCase() === 'EXPIRED' ? 'EXPIRED' : 'ACTIVE';
        return {
          ...policy,
          status: normalizedStatus
        } as PolicyResponse;
      });
  }
}
