import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClaimResponse } from '../../core/models/claim.models';
import { PaymentMethod, PaymentResponse } from '../../core/models/payment.models';
import { AuthService } from '../../core/services/auth.service';
import { ClaimReferenceService } from '../../core/services/claim-reference.service';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { PaymentsApiService } from '../../core/services/payments-api.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, DatePipe, DecimalPipe, ShellCardComponent],
  templateUrl: './payments.component.html',
  styleUrl: './payments.component.scss'
})
export class PaymentsComponent implements OnInit, OnDestroy {
  readonly paymentForm = this.fb.nonNullable.group({
    claimReference: ['', [Validators.required]],
    policyholderId: [0, [Validators.min(0)]],
    amount: [0, [Validators.min(0)]],
    paymentMethod: ['BANK_TRANSFER' as PaymentMethod, [Validators.required]],
    beneficiaryName: ['', [Validators.required]],
    accountNumber: ['', [Validators.required]],
    ifscCode: ['', [Validators.required]],
    bankName: ['']
  });

  readonly filterForm = this.fb.nonNullable.group({
    claimReference: ['', [Validators.required]]
  });

  readonly paymentMethods: PaymentMethod[] = ['BANK_TRANSFER', 'CHEQUE', 'UPI', 'NEFT', 'RTGS', 'IMPS'];
  payments: PaymentResponse[] = [];
  isSubmitting = false;
  isLoading = false;
  error = '';
  success = '';
  resolvedClaimNumber = '';
  resolvedClaimId: number | null = null;
  resolvedClaimStatus = '';
  resolvedApprovedAmount = 0;
  isBankDetailsLoading = false;
  bankDetailsHint = '';
  serverValidationErrors: Record<string, string> = {};
  private claimReferenceSubscription?: Subscription;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly claimReferenceService: ClaimReferenceService,
    private readonly paymentsApiService: PaymentsApiService,
    private readonly claimsApiService: ClaimsApiService
  ) {}

  ngOnInit(): void {
    if (!this.canCreateSettlement) {
      return;
    }
    this.claimReferenceSubscription = this.paymentForm.controls.claimReference.valueChanges
      .pipe(debounceTime(500), distinctUntilChanged())
      .subscribe((value) => {
        const reference = value.trim();
        if (!reference) {
          this.bankDetailsHint = '';
          return;
        }
        this.tryResolveAndPrefill(reference);
      });
  }

  ngOnDestroy(): void {
    this.claimReferenceSubscription?.unsubscribe();
  }

  get canCreateSettlement(): boolean {
    return this.authService.hasRole('ROLE_MANAGER');
  }

  submitPayment(): void {
    if (!this.canCreateSettlement) {
      this.error = 'Settlement creation is restricted to manager role.';
      return;
    }
    if (this.paymentForm.invalid || this.isSubmitting) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.error = '';
    this.success = '';
    this.serverValidationErrors = {};

    const values = this.paymentForm.getRawValue();
    this.resolvedClaimNumber = '';
    this.resolvedClaimId = null;
    this.resolvedClaimStatus = '';
    this.resolvedApprovedAmount = 0;
    this.bankDetailsHint = '';

    this.claimReferenceService.resolveClaim(values.claimReference).subscribe({
      next: (claim) => {
        this.updateResolvedClaimState(claim);
        this.prefillSettlementBankDetails(claim.id, true);

        if (!this.validateClaimSettlementEligibility(claim)) {
          this.isSubmitting = false;
          return;
        }

        const policyholderId = values.policyholderId > 0 ? values.policyholderId : claim.policyholderId;
        if (values.policyholderId > 0 && values.policyholderId !== claim.policyholderId) {
          this.error = `Policyholder ID must match claim owner (${claim.policyholderId}).`;
          this.isSubmitting = false;
          return;
        }
        const amount = values.amount > 0 ? values.amount : claim.approvedAmount ?? 0;

        if (!policyholderId || policyholderId <= 0) {
          this.error = 'Policyholder ID could not be resolved for this claim.';
          this.isSubmitting = false;
          return;
        }

        if (!amount || amount <= 0) {
          this.error = 'Settlement amount could not be resolved for this claim.';
          this.isSubmitting = false;
          return;
        }
        if ((claim.approvedAmount ?? 0) > 0 && amount > (claim.approvedAmount ?? 0)) {
          this.error = `Settlement amount cannot exceed approved amount (${claim.approvedAmount}).`;
          this.isSubmitting = false;
          return;
        }

        this.paymentsApiService
          .createPayment({
            claimId: claim.id,
            policyholderId,
            amount,
            paymentMethod: values.paymentMethod,
            beneficiaryName: values.beneficiaryName,
            accountNumber: values.accountNumber,
            ifscCode: values.ifscCode,
            bankName: values.bankName
          })
          .subscribe({
            next: (response) => {
              const payment = response.data;
              const status = (payment?.status ?? '').toUpperCase();
              if (status === 'APPROVED') {
                this.success = `Settlement instruction submitted for claim ${claim.claimNumber}.`;
                this.error = '';
              } else {
                const failureReason = payment?.failureReason ? ` Reason: ${payment.failureReason}` : '';
                const displayStatus = status ? this.formatStatus(status) : 'Unknown';
                this.success = '';
                this.error = `Settlement instruction for claim ${claim.claimNumber} ended with status ${displayStatus}.${failureReason}`;
              }
              this.isSubmitting = false;
              this.loadByClaim(claim.id);
            },
            error: (error: { error?: { message?: string } }) => {
              this.serverValidationErrors = extractValidationErrors(error);
              this.error = toUserErrorMessage(
                error,
                'Settlement instruction could not be submitted.',
                'Please review settlement details and enter valid values.'
              );
              this.isSubmitting = false;
            }
          });
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(
          error,
          'Claim reference could not be validated.',
          'Enter a valid Claim ID or Claim number.'
        );
        this.isSubmitting = false;
      }
    });
  }

  loadPayments(): void {
    if (this.filterForm.invalid) {
      this.filterForm.markAllAsTouched();
      return;
    }
    this.error = '';
    const { claimReference } = this.filterForm.getRawValue();
    this.claimReferenceService.resolveClaim(claimReference).subscribe({
      next: (claim) => {
        this.updateResolvedClaimState(claim);
        this.prefillSettlementBankDetails(claim.id, true);
        this.loadByClaim(claim.id);
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(
          error,
          'Claim reference could not be validated.',
          'Enter a valid Claim ID or Claim number.'
        );
      }
    });
  }

  private loadByClaim(claimId: number): void {
    this.isLoading = true;
    this.error = '';
    this.paymentsApiService.getPaymentsByClaim(claimId).subscribe({
      next: (response: { data: PaymentResponse[] }) => {
        this.payments = response.data;
        this.isLoading = false;
      },
      error: (error: { error?: { message?: string } }) => {
        this.error = toUserErrorMessage(error, 'Unable to load settlements for this claim.');
        this.isLoading = false;
      }
    });
  }

  fieldError(formName: 'payment' | 'filter', field: string, label: string): string {
    const form = formName === 'payment' ? this.paymentForm : this.filterForm;
    return getFieldError(form, field, label, this.serverValidationErrors);
  }

  formatPaymentMethod(method: PaymentMethod): string {
    return method.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  private validateClaimSettlementEligibility(claim: ClaimResponse): boolean {
    if (claim.status !== 'APPROVED') {
      this.error = `Settlement is allowed only for Approved claims. Current status: ${this.formatStatus(claim.status)}.`;
      return false;
    }
    if (!claim.approvedAmount || claim.approvedAmount <= 0) {
      this.error = 'Approved amount is missing for this claim. Settlement cannot proceed.';
      return false;
    }
    return true;
  }

  private tryResolveAndPrefill(reference: string): void {
    this.claimReferenceService.resolveClaim(reference).subscribe({
      next: (claim) => {
        this.updateResolvedClaimState(claim);
        this.prefillSettlementBankDetails(claim.id, false);
      },
      error: () => {
        // Ignore while user is still entering claim reference.
      }
    });
  }

  private updateResolvedClaimState(claim: ClaimResponse): void {
    this.resolvedClaimId = claim.id;
    this.resolvedClaimNumber = claim.claimNumber;
    this.resolvedClaimStatus = claim.status;
    this.resolvedApprovedAmount = claim.approvedAmount ?? 0;
  }

  private prefillSettlementBankDetails(claimId: number, silent: boolean): void {
    this.isBankDetailsLoading = true;
    this.bankDetailsHint = '';
    this.claimsApiService.getClaimBankDetails(claimId).subscribe({
      next: (response) => {
        const details = response.data;
        const current = this.paymentForm.getRawValue();
        this.paymentForm.patchValue(
          {
            beneficiaryName: current.beneficiaryName.trim() ? current.beneficiaryName : details.beneficiaryName,
            accountNumber: current.accountNumber.trim() ? current.accountNumber : details.accountNumber,
            ifscCode: current.ifscCode.trim() ? current.ifscCode : details.ifscCode,
            bankName: current.bankName.trim() ? current.bankName : details.bankName
          },
          { emitEvent: false }
        );
        this.bankDetailsHint = 'Bank details auto-filled from policyholder profile.';
        this.isBankDetailsLoading = false;
      },
      error: () => {
        this.isBankDetailsLoading = false;
        if (!silent) {
          this.bankDetailsHint = 'No saved bank details found for this claim. Enter them manually.';
        }
      }
    });
  }
}
