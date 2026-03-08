export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'ADJUSTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';

export interface ClaimCreateRequest {
  policyNumber: string;
  vehicleRegistration: string;
  vehicleMake?: string;
  vehicleModel?: string;
  vehicleYear?: number | null;
  incidentDate: string;
  incidentLocation?: string;
  incidentDescription?: string;
  claimAmount: number;
}

export interface ClaimResponse {
  id: number;
  claimNumber: string;
  policyNumber: string;
  policyholderId: number;
  policyholderName?: string | null;
  policyholderEmail?: string | null;
  policyholderPhone?: string | null;
  status: ClaimStatus;
  claimAmount: number;
  approvedAmount?: number | null;
  incidentDate: string;
  incidentLocation?: string | null;
  incidentDescription?: string | null;
  assignedAdjusterId?: number | null;
  adjusterName?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ClaimStatusUpdateRequest {
  status: ClaimStatus;
  notes?: string;
  approvedAmount?: number;
  assignedAdjusterId?: number;
}

export interface ClaimBankDetailsRequest {
  beneficiaryName: string;
  accountNumber: string;
  ifscCode: string;
  bankName: string;
}

export interface ClaimBankDetailsResponse {
  claimId: number;
  beneficiaryName: string;
  accountNumber: string;
  ifscCode: string;
  bankName: string;
  updatedAt?: string | null;
}

export type ClaimAuditAction =
  | 'CLAIM_SUBMITTED'
  | 'CLAIM_UPDATED'
  | 'CLAIM_STATUS_UPDATED'
  | 'CLAIM_ASSIGNED'
  | 'CLAIM_UNASSIGNED';

export interface ClaimAuditEvent {
  id: number;
  claimId: number;
  claimNumber: string;
  actionType: ClaimAuditAction;
  oldStatus?: ClaimStatus | null;
  newStatus?: ClaimStatus | null;
  assignedAdjusterId?: number | null;
  approvedAmount?: number | null;
  details?: string | null;
  changedBy: string;
  createdAt: string;
}
