import { Injectable } from '@angular/core';
import { Observable, map, throwError } from 'rxjs';
import { ClaimResponse } from '../models/claim.models';
import { ClaimsApiService } from './claims-api.service';

@Injectable({
  providedIn: 'root'
})
export class ClaimReferenceService {
  constructor(private readonly claimsApiService: ClaimsApiService) {}

  resolveClaim(reference: string): Observable<ClaimResponse> {
    const normalized = reference.trim();
    if (!normalized) {
      return throwError(() => new Error('Enter a claim reference.'));
    }

    if (this.isNumeric(normalized)) {
      const claimId = Number(normalized);
      if (!Number.isInteger(claimId) || claimId <= 0) {
        return throwError(() => new Error('Claim ID must be a positive number.'));
      }
      return this.claimsApiService.getClaimById(claimId).pipe(map((response) => response.data));
    }

    return this.claimsApiService.getClaimByNumber(normalized).pipe(map((response) => response.data));
  }

  private isNumeric(value: string): boolean {
    return /^\d+$/.test(value);
  }
}
