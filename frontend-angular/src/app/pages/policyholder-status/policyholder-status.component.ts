import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';
import { ClaimBankDetailsRequest, ClaimResponse } from '../../core/models/claim.models';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { ToastService } from '../../core/services/toast.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-policyholder-status',
  standalone: true,
  imports: [NgIf, NgFor, DatePipe, DecimalPipe, FormsModule, ShellCardComponent],
  templateUrl: './policyholder-status.component.html',
  styleUrl: './policyholder-status.component.scss'
})
export class PolicyholderStatusComponent implements OnInit, OnDestroy {
  claims: ClaimResponse[] = [];
  bankDetailsByClaim: Record<number, ClaimBankDetailsRequest> = {};
  bankDetailsSavingByClaim: Record<number, boolean> = {};
  bankDetailsErrorByClaim: Record<number, string> = {};
  bankDetailsSuccessByClaim: Record<number, string> = {};
  private bankDetailsLoadedClaimIds = new Set<number>();
  isLoading = false;
  error = '';
  lastUpdated: Date | null = null;
  private refreshSubscription?: Subscription;

  constructor(
    private readonly claimsApiService: ClaimsApiService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadStatuses();
    this.refreshSubscription = interval(15000).subscribe(() => this.loadStatuses(true));
  }

  ngOnDestroy(): void {
    this.refreshSubscription?.unsubscribe();
  }

  onRefreshStatuses(): void {
    this.loadStatuses(false, true);
  }

  loadStatuses(silent = false, showToast = false): void {
    if (!silent) {
      this.isLoading = true;
    }
    this.claimsApiService.getMyClaims().subscribe({
      next: (response) => {
        this.claims = [...response.data].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        this.seedBankDetailsDrafts(this.claims);
        this.lastUpdated = new Date();
        this.error = '';
        if (showToast) {
          this.toastService.showSuccess('Claim statuses refreshed.');
        }
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to refresh claim status.');
        if (showToast) {
          this.toastService.showError(this.error);
        }
        this.isLoading = false;
      }
    });
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  stepClass(status: string, step: 'submitted' | 'review' | 'decision' | 'paid'): string {
    const normalized = status.toUpperCase();
    const isRejectedPath = normalized === 'REJECTED' || normalized === 'CANCELLED';
    const isApprovedPath = ['APPROVED', 'PAID', 'PAYMENT_FAILED'].includes(normalized);

    if (step === 'submitted') {
      return 'done';
    }
    if (step === 'review') {
      return ['UNDER_REVIEW', 'ADJUSTED', 'APPROVED', 'REJECTED', 'PAID', 'PAYMENT_FAILED', 'CANCELLED'].includes(
        normalized
      )
        ? 'done'
        : 'pending';
    }
    if (step === 'decision') {
      if (isRejectedPath) {
        return 'rejected';
      }
      return isApprovedPath ? 'done' : 'pending';
    }
    if (step === 'paid') {
      if (normalized === 'PAYMENT_FAILED') {
        return 'failed';
      }
      return normalized === 'PAID' ? 'done' : 'pending';
    }
    return 'pending';
  }

  decisionLabel(status: string): string {
    const normalized = status.toUpperCase();
    if (normalized === 'REJECTED' || normalized === 'CANCELLED') {
      return 'Rejected';
    }
    return 'Approved';
  }

  shouldShowBankDetails(claim: ClaimResponse): boolean {
    return claim.status === 'APPROVED' || claim.status === 'PAYMENT_FAILED';
  }

  saveBankDetails(claim: ClaimResponse): void {
    if (!this.shouldShowBankDetails(claim) || this.bankDetailsSavingByClaim[claim.id]) {
      return;
    }

    const draft = this.bankDetailsByClaim[claim.id] ?? this.newBankDetailsDraft();
    const validation = this.validateBankDetails(draft);
    if (validation) {
      this.bankDetailsErrorByClaim[claim.id] = validation;
      this.bankDetailsSuccessByClaim[claim.id] = '';
      return;
    }

    this.bankDetailsSavingByClaim[claim.id] = true;
    this.bankDetailsErrorByClaim[claim.id] = '';
    this.bankDetailsSuccessByClaim[claim.id] = '';

    this.claimsApiService.updateClaimBankDetails(claim.id, draft).subscribe({
      next: () => {
        this.bankDetailsSavingByClaim[claim.id] = false;
        this.bankDetailsSuccessByClaim[claim.id] = 'Bank details saved successfully.';
      },
      error: (error: unknown) => {
        this.bankDetailsSavingByClaim[claim.id] = false;
        this.bankDetailsErrorByClaim[claim.id] = toUserErrorMessage(error, 'Unable to save bank details.');
      }
    });
  }

  private seedBankDetailsDrafts(claims: ClaimResponse[]): void {
    const eligibleClaimIds = new Set<number>();
    for (const claim of claims) {
      if (!this.shouldShowBankDetails(claim)) {
        continue;
      }
      eligibleClaimIds.add(claim.id);
      this.bankDetailsByClaim[claim.id] = this.bankDetailsByClaim[claim.id] ?? this.newBankDetailsDraft();
      if (!this.bankDetailsLoadedClaimIds.has(claim.id)) {
        this.bankDetailsLoadedClaimIds.add(claim.id);
        this.loadBankDetails(claim.id);
      }
    }

    for (const claimId of Object.keys(this.bankDetailsByClaim).map((value) => Number(value))) {
      if (!eligibleClaimIds.has(claimId)) {
        delete this.bankDetailsByClaim[claimId];
        delete this.bankDetailsSavingByClaim[claimId];
        delete this.bankDetailsErrorByClaim[claimId];
        delete this.bankDetailsSuccessByClaim[claimId];
      }
    }
  }

  private loadBankDetails(claimId: number): void {
    this.claimsApiService.getClaimBankDetails(claimId).subscribe({
      next: (response) => {
        this.bankDetailsByClaim[claimId] = {
          beneficiaryName: response.data.beneficiaryName ?? '',
          accountNumber: response.data.accountNumber ?? '',
          ifscCode: response.data.ifscCode ?? '',
          bankName: response.data.bankName ?? ''
        };
      },
      error: () => {
        // No stored bank details yet is acceptable for first-time entry.
      }
    });
  }

  private validateBankDetails(details: ClaimBankDetailsRequest): string {
    if (!details.beneficiaryName.trim()) {
      return 'Beneficiary name is required.';
    }
    if (!/^[0-9]{6,20}$/.test(details.accountNumber.trim())) {
      return 'Account number must be 6 to 20 digits.';
    }
    if (!/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/.test(details.ifscCode.trim())) {
      return 'IFSC code format is invalid.';
    }
    if (!details.bankName.trim()) {
      return 'Bank name is required.';
    }
    return '';
  }

  private newBankDetailsDraft(): ClaimBankDetailsRequest {
    return {
      beneficiaryName: '',
      accountNumber: '',
      ifscCode: '',
      bankName: ''
    };
  }
}
