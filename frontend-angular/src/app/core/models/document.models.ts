export type DocumentType =
  | 'CLAIM_FORM'
  | 'POLICY_DOCUMENT'
  | 'ID_PROOF'
  | 'VEHICLE_REGISTRATION'
  | 'DRIVERS_LICENSE'
  | 'POLICE_REPORT'
  | 'MEDICAL_REPORT'
  | 'REPAIR_ESTIMATE'
  | 'PHOTO_EVIDENCE'
  | 'OTHER';

export interface DocumentResponse {
  id: number;
  claimId: number;
  uploadedBy: number;
  fileName: string;
  originalFileName: string;
  fileType: string;
  mimeType: string;
  fileSize: number;
  documentType: DocumentType;
  description?: string | null;
  createdAt: string;
  downloadUrl?: string | null;
}
