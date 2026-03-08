export type AssessmentDecision = 'APPROVED' | 'REJECTED' | 'ADJUSTED' | 'PENDING_REVIEW';
export type AssessmentRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AssessmentValidationStatus = 'VALID' | 'INVALID' | 'REQUIRES_DOCUMENTS' | 'REQUIRES_INSPECTION';
export type AdjustmentType =
  | 'DEPRECIATION_APPLIED'
  | 'EXCESS_DEDUCTED'
  | 'PART_DAMAGE_ONLY'
  | 'PRE_EXISTING_DAMAGE'
  | 'BETTERMENT_APPLIED'
  | 'POLICY_LIMIT_REACHED'
  | 'DUPLICATE_CLAIM'
  | 'INVESTIGATION_REQUIRED'
  | 'DOCUMENTATION_INCOMPLETE'
  | 'OTHER';

export interface AssessmentRequest {
  claimId: number;
  assessedAmount: number;
  justification?: string;
  notes?: string;
}

export interface DecisionRequest {
  assessmentId: number;
  decision: AssessmentDecision;
  finalAmount?: number;
  justification?: string;
}

export interface AdjustmentRequest {
  assessmentId: number;
  claimId: number;
  adjustedAmount: number;
  adjustmentType: AdjustmentType;
  reason: string;
  detailedNotes?: string;
}

export interface AdjustmentResponse {
  id: number;
  assessmentId: number;
  claimId: number;
  previousAmount?: number | null;
  adjustedAmount: number;
  differenceAmount?: number | null;
  adjustmentType: AdjustmentType;
  reason: string;
  detailedNotes?: string | null;
  adjustedBy: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AssessmentResponse {
  id: number;
  claimId: number;
  assessorId: number;
  riskScore?: number | null;
  riskLevel?: AssessmentRiskLevel | null;
  decision?: AssessmentDecision | null;
  assessedAmount?: number | null;
  recommendedAmount?: number | null;
  justification?: string | null;
  notes?: string | null;
  validationStatus?: AssessmentValidationStatus | null;
  ruleViolations?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  adjustments?: AdjustmentResponse[] | null;
}
