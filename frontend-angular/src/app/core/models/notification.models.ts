export type NotificationType =
  | 'CLAIM_STATUS_UPDATE'
  | 'CLAIM_APPROVED'
  | 'CLAIM_REJECTED'
  | 'PAYMENT_PROCESSED'
  | 'DOCUMENT_UPLOADED'
  | 'ASSESSMENT_COMPLETED'
  | 'SYSTEM_MESSAGE'
  | 'REMINDER';

export interface NotificationMessage {
  id: number;
  userId: number;
  claimId?: number | null;
  title: string;
  message: string;
  type: NotificationType;
  status?: string | null;
  isRead?: boolean | null;
  readAt?: string | null;
  actionUrl?: string | null;
  senderId?: number | null;
  createdAt?: string | null;
}
