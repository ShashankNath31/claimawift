import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ClaimResponse } from '../../core/models/claim.models';
import { DocumentResponse } from '../../core/models/document.models';
import { PaymentMethod, PaymentResponse } from '../../core/models/payment.models';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { DocumentsApiService } from '../../core/services/documents-api.service';
import { PaymentsApiService } from '../../core/services/payments-api.service';
import { ToastService } from '../../core/services/toast.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-policyholder-history',
  standalone: true,
  imports: [NgIf, NgFor, DatePipe, DecimalPipe, ShellCardComponent],
  templateUrl: './policyholder-history.component.html',
  styleUrl: './policyholder-history.component.scss'
})
export class PolicyholderHistoryComponent implements OnInit {
  history: ClaimResponse[] = [];
  documents: DocumentResponse[] = [];
  payments: PaymentResponse[] = [];
  selectedClaim: ClaimResponse | null = null;
  isLoading = false;
  isLoadingRecords = false;
  error = '';
  currentPage = 1;
  readonly pageSize = 5;

  constructor(
    private readonly claimsApiService: ClaimsApiService,
    private readonly documentsApiService: DocumentsApiService,
    private readonly paymentsApiService: PaymentsApiService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  onRefreshHistory(): void {
    this.loadHistory(true);
  }

  loadHistory(showSuccessToast = false): void {
    this.isLoading = true;
    this.error = '';
    this.claimsApiService.getClaimHistory().subscribe({
      next: (response) => {
        this.history = response.data;
        this.currentPage = 1;
        if (showSuccessToast) {
          this.toastService.showSuccess('Claim history refreshed.');
        }
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim history.');
        this.toastService.showError(this.error);
        this.isLoading = false;
      }
    });
  }

  viewClaimRecords(claim: ClaimResponse): void {
    this.selectedClaim = claim;
    this.loadAssociatedRecords(claim.id);
  }

  downloadDocument(doc: DocumentResponse): void {
    this.documentsApiService.downloadDocument(doc.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = doc.originalFileName || doc.fileName;
        anchor.click();
        window.URL.revokeObjectURL(url);
        this.toastService.showSuccess('Document download started.');
      },
      error: () => {
        this.error = 'Document download failed. Please try again.';
        this.toastService.showError(this.error);
      }
    });
  }

  downloadSettlementDocument(claim: ClaimResponse): void {
    this.documentsApiService.getDocumentsByClaim(claim.id).subscribe({
      next: (response) => {
        const settlementDoc = this.findSettlementDocument(response.data);
        if (!settlementDoc) {
          this.toastService.showInfo('No settlement document is available for this claim yet.');
          return;
        }
        this.downloadDocument(settlementDoc);
      },
      error: () => {
        this.toastService.showError('Unable to load settlement documents.');
      }
    });
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatPaymentMethod(method: PaymentMethod): string {
    return method.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  isPaidClaim(claim: ClaimResponse): boolean {
    return claim.status.toUpperCase() === 'PAID';
  }

  get pagedHistory(): ClaimResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.history.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.history.length / this.pageSize));
  }

  goToPreviousPage(): void {
    this.currentPage = Math.max(1, this.currentPage - 1);
  }

  goToNextPage(): void {
    this.currentPage = Math.min(this.totalPages, this.currentPage + 1);
  }

  private loadAssociatedRecords(claimId: number): void {
    this.isLoadingRecords = true;
    this.documentsApiService.getDocumentsByClaim(claimId).subscribe({
      next: (documentsResponse) => {
        this.documents = documentsResponse.data;
        this.paymentsApiService.getPaymentsByClaim(claimId).subscribe({
          next: (paymentsResponse) => {
            this.payments = paymentsResponse.data;
            this.isLoadingRecords = false;
          },
          error: (error: unknown) => {
            this.error = toUserErrorMessage(error, 'Unable to load settlement records.');
            this.toastService.showError(this.error);
            this.isLoadingRecords = false;
          }
        });
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim documents.');
        this.toastService.showError(this.error);
        this.isLoadingRecords = false;
      }
    });
  }

  private findSettlementDocument(documents: DocumentResponse[]): DocumentResponse | null {
    const keywords = ['settlement', 'receipt', 'payout', 'payment'];
    const settlementDocuments = documents.filter((doc) => {
      const values = [doc.fileName, doc.originalFileName, doc.description]
        .filter((value): value is string => !!value)
        .map((value) => value.toLowerCase());
      return values.some((value) => keywords.some((keyword) => value.includes(keyword)));
    });
    return settlementDocuments[0] ?? null;
  }
}
