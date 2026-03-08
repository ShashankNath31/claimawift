import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { AuthService } from './core/services/auth.service';
import { ClaimsApiService } from './core/services/claims-api.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthResponse, LoginChallengeResponse } from './core/models/auth.models';
import { StandardResponse } from './core/models/common.models';
import { ClaimCreateRequest, ClaimResponse } from './core/models/claim.models';
import { PolicyPortfolioResponse } from './core/models/policy.models';

describe('Policyholder Flow (E2E style)', () => {
  let authService: AuthService;
  let claimsApiService: ClaimsApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });

    authService = TestBed.inject(AuthService);
    claimsApiService = TestBed.inject(ClaimsApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should complete login -> policies -> claim submit -> status/history read flow', () => {
    const accessToken = buildJwtWithFutureExpiry();
    const authData: AuthResponse = {
      accessToken,
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: 21,
        username: 'policyholder21',
        email: 'policyholder21@test.local',
        roles: ['ROLE_POLICYHOLDER'],
        status: 'ACTIVE'
      }
    };
    const loginChallenge: LoginChallengeResponse = {
      challengeId: 'challenge-otp-1',
      maskedEmail: 'p***1@test.local',
      expiresInSeconds: 300
    };

    authService.login({ usernameOrEmail: 'policyholder21', password: 'Test@12345' }).subscribe((response) => {
      expect(response.data.challengeId).toBe('challenge-otp-1');
    });
    const loginRequest = httpMock.expectOne('/api/auth/login');
    expect(loginRequest.request.method).toBe('POST');
    expect(loginRequest.request.headers.has('Authorization')).toBeFalse();
    loginRequest.flush(successEnvelope(loginChallenge));

    expect(authService.isAuthenticated()).toBeFalse();

    authService.verifyLoginOtp({ challengeId: 'challenge-otp-1', otp: '123456' }).subscribe();
    const verifyRequest = httpMock.expectOne('/api/auth/login/verify-otp');
    expect(verifyRequest.request.method).toBe('POST');
    expect(verifyRequest.request.headers.has('Authorization')).toBeFalse();
    verifyRequest.flush(successEnvelope(authData));

    expect(authService.isAuthenticated()).toBeTrue();
    expect(authService.hasRole('ROLE_POLICYHOLDER')).toBeTrue();

    const policiesData: PolicyPortfolioResponse = {
      totalPolicies: 2,
      activePolicies: 2,
      expiredPolicies: 0,
      policies: [
        {
          id: 1,
          policyNumber: 'POL-21-A1',
          policyType: 'Motor Insurance',
          planName: 'Comprehensive Plus',
          insurerName: 'ClaimSwift Insurance Co.',
          vehicleRegistration: 'MH12AB1234',
          coverageAmount: 500000,
          claimedAmount: 0,
          availableCoverage: 500000,
          startDate: '2026-01-01',
          expiryDate: '2026-12-31',
          status: 'ACTIVE'
        },
        {
          id: 2,
          policyNumber: 'POL-21-A2',
          policyType: 'Motor Insurance',
          planName: 'Premium Shield',
          insurerName: 'ClaimSwift Insurance Co.',
          vehicleRegistration: 'DL08CD6789',
          coverageAmount: 750000,
          claimedAmount: 0,
          availableCoverage: 750000,
          startDate: '2026-01-01',
          expiryDate: '2027-01-31',
          status: 'ACTIVE'
        }
      ]
    };

    claimsApiService.getMyPolicies().subscribe((response) => {
      expect(response.data.totalPolicies).toBe(2);
      expect(response.data.activePolicies).toBe(2);
    });
    const policiesRequest = httpMock.expectOne('/api/claims/policies/my');
    expect(policiesRequest.request.method).toBe('GET');
    expect(policiesRequest.request.headers.get('Authorization')).toBe(`Bearer ${accessToken}`);
    policiesRequest.flush(successEnvelope(policiesData));

    const claimPayload: ClaimCreateRequest = {
      policyNumber: 'POL-21-A1',
      vehicleRegistration: 'MH12AB1234',
      incidentDate: '2026-03-01',
      incidentLocation: 'Pune',
      incidentDescription: 'Minor accident',
      claimAmount: 25000
    };

    const submittedClaim: ClaimResponse = {
      id: 3001,
      claimNumber: 'CLM-3001',
      policyNumber: 'POL-21-A1',
      policyholderId: 21,
      status: 'SUBMITTED',
      claimAmount: 25000,
      approvedAmount: null,
      incidentDate: '2026-03-01',
      incidentLocation: 'Pune',
      incidentDescription: 'Minor accident',
      createdAt: '2026-03-07T09:00:00Z'
    };

    claimsApiService.createClaim(claimPayload).subscribe((response) => {
      expect(response.data.claimNumber).toBe('CLM-3001');
      expect(response.data.status).toBe('SUBMITTED');
    });
    const submitRequest = httpMock.expectOne('/api/claims');
    expect(submitRequest.request.method).toBe('POST');
    expect(submitRequest.request.headers.get('Authorization')).toBe(`Bearer ${accessToken}`);
    submitRequest.flush(successEnvelope(submittedClaim));

    claimsApiService.getMyClaims().subscribe((response) => {
      expect(response.data.length).toBe(1);
      expect(response.data[0].claimNumber).toBe('CLM-3001');
    });
    const myClaimsRequest = httpMock.expectOne('/api/claims/my-claims');
    expect(myClaimsRequest.request.method).toBe('GET');
    expect(myClaimsRequest.request.headers.get('Authorization')).toBe(`Bearer ${accessToken}`);
    myClaimsRequest.flush(successEnvelope([submittedClaim]));

    claimsApiService.getClaimHistory().subscribe((response) => {
      expect(response.data.length).toBe(1);
      expect(response.data[0].policyNumber).toBe('POL-21-A1');
    });
    const historyRequest = httpMock.expectOne('/api/claims/history');
    expect(historyRequest.request.method).toBe('GET');
    expect(historyRequest.request.headers.get('Authorization')).toBe(`Bearer ${accessToken}`);
    historyRequest.flush(successEnvelope([submittedClaim]));
  });
});

function successEnvelope<T>(data: T): StandardResponse<T> {
  return {
    code: 'SUCCESS',
    message: 'Success',
    data
  };
}

function buildJwtWithFutureExpiry(): string {
  const header = base64UrlEncode({ alg: 'HS256', typ: 'JWT' });
  const payload = base64UrlEncode({
    sub: '21',
    userId: 21,
    username: 'policyholder21',
    roles: ['ROLE_POLICYHOLDER'],
    role: 'ROLE_POLICYHOLDER',
    exp: Math.floor(Date.now() / 1000) + 3600
  });
  return `${header}.${payload}.signature`;
}

function base64UrlEncode(value: unknown): string {
  const encoded = btoa(JSON.stringify(value));
  return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
