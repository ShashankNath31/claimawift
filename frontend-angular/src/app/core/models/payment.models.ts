export type PaymentMethod = 'BANK_TRANSFER' | 'CHEQUE' | 'UPI' | 'NEFT' | 'RTGS' | 'IMPS';

export interface PaymentRequest {
  claimId: number;
  policyholderId: number;
  amount: number;
  paymentMethod: PaymentMethod;
  beneficiaryName: string;
  accountNumber: string;
  ifscCode: string;
  bankName?: string;
}

export interface PaymentResponse {
  id: number;
  transactionId: string;
  claimId: number;
  policyholderId: number;
  amount: number;
  status: string;
  paymentMethod: PaymentMethod;
  beneficiaryName: string;
  referenceNumber?: string | null;
  failureReason?: string | null;
  createdAt: string;
}
