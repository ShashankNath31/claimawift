export type PolicyStatus = 'ACTIVE' | 'EXPIRED';

export interface PolicyResponse {
  id: number;
  policyNumber: string;
  policyType: string;
  planName: string;
  insurerName: string;
  vehicleRegistration: string;
  coverageAmount: number;
  claimedAmount: number;
  availableCoverage: number;
  startDate: string;
  expiryDate: string;
  status: PolicyStatus;
}

export interface PolicyPortfolioResponse {
  totalPolicies: number;
  activePolicies: number;
  expiredPolicies: number;
  policies: PolicyResponse[];
}
