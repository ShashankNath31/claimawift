export interface ClaimSummaryReport {
  reportGeneratedAt: string;
  reportPeriod: string;
  totalClaims: number;
  submittedClaims: number;
  underReviewClaims: number;
  approvedClaims: number;
  rejectedClaims: number;
  paidClaims: number;
  totalClaimAmount: number;
  totalApprovedAmount: number;
  totalPaidAmount: number;
  averageClaimAmount: number;
}

export interface PaymentReport {
  reportGeneratedAt: string;
  reportPeriod: string;
  totalPayments: number;
  approvedPayments: number;
  rejectedPayments: number;
  pendingPayments: number;
  totalAmount: number;
  approvedAmount: number;
  rejectedAmount: number;
  averagePaymentAmount: number;
}

export interface AdjusterPerformanceEntry {
  adjusterId: number;
  adjusterName: string;
  adjusterEmail: string;
  totalClaimsAssigned: number;
  claimsApproved: number;
  claimsRejected: number;
  claimsPending: number;
  averageProcessingTimeDays: number;
  approvalRate: number;
}

export interface AdjusterPerformanceReport {
  reportGeneratedAt: string;
  reportPeriod: string;
  totalAdjusters: number;
  totalClaimsProcessed: number;
  totalAmountProcessed: number;
  averageProcessingTime: number;
  adjusterPerformances: AdjusterPerformanceEntry[];
}
