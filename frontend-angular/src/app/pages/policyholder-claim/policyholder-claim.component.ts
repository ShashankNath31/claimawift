import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClaimCreateRequest, ClaimResponse } from '../../core/models/claim.models';
import { StandardResponse } from '../../core/models/common.models';
import { DocumentType } from '../../core/models/document.models';
import { PolicyPortfolioResponse, PolicyResponse } from '../../core/models/policy.models';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { DocumentsApiService } from '../../core/services/documents-api.service';
import { PolicyMockService } from '../../core/services/policy-mock.service';
import { ToastService } from '../../core/services/toast.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-policyholder-claim',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, DatePipe, DecimalPipe, ShellCardComponent],
  templateUrl: './policyholder-claim.component.html',
  styleUrl: './policyholder-claim.component.scss'
})
export class PolicyholderClaimComponent implements OnInit {
  readonly claimForm = this.fb.nonNullable.group({
    policyNumber: ['', [Validators.required]],
    incidentDate: ['', [Validators.required]],
    incidentLocation: [''],
    incidentDescription: [''],
    claimAmount: [0, [Validators.required, Validators.min(1)]]
  });

  readonly evidenceForm = this.fb.nonNullable.group({
    documentType: ['CLAIM_FORM' as DocumentType, [Validators.required]],
    description: ['']
  });

  readonly documentTypes: DocumentType[] = [
    'CLAIM_FORM',
    'POLICY_DOCUMENT',
    'ID_PROOF',
    'VEHICLE_REGISTRATION',
    'DRIVERS_LICENSE',
    'POLICE_REPORT',
    'MEDICAL_REPORT',
    'REPAIR_ESTIMATE',
    'PHOTO_EVIDENCE',
    'OTHER'
  ];

  claims: ClaimResponse[] = [];
  policies: PolicyResponse[] = [];
  selectedPolicy: PolicyResponse | null = null;
  selectedFile: File | null = null;
  pendingEvidenceClaim: ClaimResponse | null = null;
  isSubmitting = false;
  isRetryingEvidence = false;
  isLoading = false;
  isPolicyLoading = false;
  error = '';
  success = '';
  fileError = '';
  policyError = '';
  policyNotice = '';
  serverValidationErrors: Record<string, string> = {};

  constructor(
    private readonly fb: FormBuilder,
    private readonly claimsApiService: ClaimsApiService,
    private readonly documentsApiService: DocumentsApiService,
    private readonly policyMockService: PolicyMockService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadPolicies();
    this.loadMyClaims();
  }

  onFileSelected(event: Event): void {
    this.fileError = '';
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.validateSelectedFile();
  }

  onClaimAmountSlider(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Number(input.value);
    if (!Number.isFinite(value)) {
      return;
    }
    const normalized = Math.max(1, Math.min(value, this.claimAmountMax));
    this.claimForm.patchValue({ claimAmount: normalized });
    this.policyError = '';
  }

  onRefreshClaims(): void {
    this.loadMyClaims(true);
  }

  submitClaim(): void {
    if (this.claimForm.invalid || this.evidenceForm.invalid || this.isSubmitting) {
      this.claimForm.markAllAsTouched();
      this.evidenceForm.markAllAsTouched();
      return;
    }

    if (!this.selectedFile) {
      this.fileError = 'Upload one evidence document (PDF) before submitting.';
      return;
    }

    if (!this.validateSelectedFile() || !this.validateIncidentDate()) {
      return;
    }

    if (!this.selectedPolicy) {
      this.policyError = 'Select an active policy before submitting a claim.';
      return;
    }

    if (this.selectedPolicy.status !== 'ACTIVE') {
      this.policyError = 'Selected policy is expired and cannot be used for claim submission.';
      return;
    }

    const requestedAmount = this.claimForm.controls.claimAmount.value;
    if (requestedAmount > this.selectedPolicy.availableCoverage) {
      this.policyError = `Requested amount exceeds available coverage (${this.selectedPolicy.availableCoverage}).`;
      return;
    }

    this.isSubmitting = true;
    this.error = '';
    this.success = '';
    this.policyError = '';
    this.policyNotice = '';
    this.serverValidationErrors = {};
    this.pendingEvidenceClaim = null;

    const rawValues = this.claimForm.getRawValue();
    const submittedPolicyNumber = rawValues.policyNumber;
    const claimPayload: ClaimCreateRequest = {
      policyNumber: rawValues.policyNumber,
      vehicleRegistration: this.selectedPolicy.vehicleRegistration,
      incidentDate: rawValues.incidentDate,
      incidentLocation: rawValues.incidentLocation,
      incidentDescription: rawValues.incidentDescription,
      claimAmount: rawValues.claimAmount
    };
    const evidencePayload = this.evidenceForm.getRawValue();

    this.claimsApiService.createClaim(claimPayload).subscribe({
      next: (response: StandardResponse<ClaimResponse>) => {
        const createdClaim = response.data;
        this.documentsApiService
          .uploadDocument(
            this.selectedFile as File,
            createdClaim.id,
            evidencePayload.documentType,
            evidencePayload.description
          )
          .subscribe({
            next: () => {
              this.success = `Claim ${createdClaim.claimNumber} submitted with evidence successfully.`;
              this.toastService.showSuccess(`Claim ${createdClaim.claimNumber} submitted successfully.`);
              this.pendingEvidenceClaim = null;
              this.resetForms(submittedPolicyNumber);
              this.isSubmitting = false;
              this.loadMyClaims(false, false);
              this.loadPolicies(submittedPolicyNumber);
            },
            error: (error: unknown) => {
              const uploadMessage = toUserErrorMessage(error, 'Evidence upload failed.');
              this.error = `Claim ${createdClaim.claimNumber} was submitted, but evidence upload failed. ${uploadMessage}`;
              this.toastService.showError(this.error);
              this.pendingEvidenceClaim = createdClaim;
              this.isSubmitting = false;
              this.loadMyClaims(false, false);
            }
          });
      },
      error: (error: unknown) => {
        this.serverValidationErrors = extractValidationErrors(error);
        this.error = toUserErrorMessage(error, 'Claim submission failed. Please check the details and try again.');
        this.toastService.showError(this.error);
        this.isSubmitting = false;
      }
    });
  }

  onPolicyChanged(policyNumber: string): void {
    this.selectedPolicy = this.policies.find((policy) => policy.policyNumber === policyNumber) ?? null;
    this.policyError = '';
    this.syncClaimAmountToCoverage();
  }

  retryEvidenceUpload(): void {
    if (!this.pendingEvidenceClaim || !this.selectedFile || this.isRetryingEvidence) {
      return;
    }

    this.isRetryingEvidence = true;
    const evidencePayload = this.evidenceForm.getRawValue();
    this.documentsApiService
      .uploadDocument(
        this.selectedFile,
        this.pendingEvidenceClaim.id,
        evidencePayload.documentType,
        evidencePayload.description
      )
      .subscribe({
        next: () => {
          this.success = `Evidence uploaded successfully for claim ${this.pendingEvidenceClaim?.claimNumber}.`;
          this.error = '';
          this.toastService.showSuccess(this.success);
          this.pendingEvidenceClaim = null;
          this.isRetryingEvidence = false;
        },
        error: (error: unknown) => {
          this.error = toUserErrorMessage(error, 'Evidence upload retry failed.');
          this.toastService.showError(this.error);
          this.isRetryingEvidence = false;
        }
      });
  }

  loadMyClaims(showSuccessToast = false, surfaceErrors = true): void {
    this.isLoading = true;
    this.claimsApiService.getMyClaims().subscribe({
      next: (response) => {
        this.claims = response.data;
        if (showSuccessToast) {
          this.toastService.showSuccess('Claims list refreshed.');
        }
        this.isLoading = false;
      },
      error: (error: unknown) => {
        if (surfaceErrors) {
          this.error = toUserErrorMessage(error, 'Unable to load your claims.');
          this.toastService.showError(this.error);
        }
        this.isLoading = false;
      }
    });
  }

  fieldError(field: string, label: string): string {
    return getFieldError(this.claimForm, field, label, this.serverValidationErrors);
  }

  formatDocumentType(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  get claimAmountMax(): number {
    if (!this.selectedPolicy) {
      return 1;
    }
    return Math.max(1, Math.floor(this.selectedPolicy.availableCoverage));
  }

  get sliderStep(): number {
    const max = this.claimAmountMax;
    if (max <= 10000) {
      return 100;
    }
    if (max <= 100000) {
      return 500;
    }
    return 1000;
  }

  get remainingCoverageAfterClaim(): number {
    if (!this.selectedPolicy) {
      return 0;
    }
    const amount = this.claimForm.controls.claimAmount.value || 0;
    return Math.max(this.selectedPolicy.availableCoverage - amount, 0);
  }

  private loadPolicies(preferredPolicyNumber?: string): void {
    this.isPolicyLoading = true;
    this.policyError = '';
    this.policyNotice = '';
    this.claimsApiService.getMyPolicies().subscribe({
      next: (response: StandardResponse<PolicyPortfolioResponse>) => {
        const activePolicies = this.filterActivePolicies(response.data.policies);
        if (activePolicies.length > 0) {
          this.setSelectablePolicies(activePolicies, preferredPolicyNumber);
          this.isPolicyLoading = false;
          return;
        }
        this.loadFallbackActivePolicies(
          'No active policies found from claim service. Showing default active policies.',
          preferredPolicyNumber
        );
      },
      error: (error: unknown) => {
        const message = toUserErrorMessage(error, 'Unable to load your policy details.');
        this.loadFallbackActivePolicies(
          message.toLowerCase().includes('temporarily unavailable')
            ? 'Claim service is temporarily unavailable. Showing default active policies.'
            : `${message} Showing default active policies.`,
          preferredPolicyNumber
        );
      }
    });
  }

  private validateIncidentDate(): boolean {
    const dateValue = this.claimForm.controls.incidentDate.value;
    if (!dateValue) {
      return false;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const incidentDate = new Date(dateValue);
    incidentDate.setHours(0, 0, 0, 0);

    if (incidentDate > today) {
      this.error = 'Incident date cannot be in the future.';
      return false;
    }

    return true;
  }

  private validateSelectedFile(): boolean {
    if (!this.selectedFile) {
      return false;
    }

    const maxSizeInBytes = 10 * 1024 * 1024;
    if (this.selectedFile.size > maxSizeInBytes) {
      this.fileError = 'Evidence file must be 10 MB or smaller.';
      return false;
    }

    const fileName = this.selectedFile.name.toLowerCase();
    const isPdfByName = fileName.endsWith('.pdf');
    const isPdfByType = this.selectedFile.type === 'application/pdf';
    if (!isPdfByName || !isPdfByType) {
      this.fileError = 'Only PDF documents are supported for evidence upload.';
      return false;
    }

    this.fileError = '';
    return true;
  }

  private resetForms(preferredPolicyNumber?: string): void {
    this.claimForm.reset({
      policyNumber: '',
      incidentDate: '',
      incidentLocation: '',
      incidentDescription: '',
      claimAmount: 0
    });
    const selectedPolicy =
      this.policies.find((policy) => policy.policyNumber === preferredPolicyNumber) ?? this.policies[0] ?? null;
    if (selectedPolicy) {
      this.claimForm.patchValue({ policyNumber: selectedPolicy.policyNumber });
      this.onPolicyChanged(selectedPolicy.policyNumber);
    } else {
      this.selectedPolicy = null;
    }
    this.evidenceForm.reset({
      documentType: 'CLAIM_FORM',
      description: ''
    });
    this.selectedFile = null;
    this.fileError = '';
  }

  private filterActivePolicies(policies: PolicyResponse[] | null | undefined): PolicyResponse[] {
    if (!Array.isArray(policies)) {
      return [];
    }
    return policies.filter((policy) => policy.status === 'ACTIVE');
  }

  private setSelectablePolicies(activePolicies: PolicyResponse[], preferredPolicyNumber?: string): void {
    this.policies = activePolicies;
    const selectedPolicy =
      this.policies.find((policy) => policy.policyNumber === preferredPolicyNumber) ?? this.policies[0] ?? null;
    if (selectedPolicy) {
      this.claimForm.patchValue({ policyNumber: selectedPolicy.policyNumber });
      this.onPolicyChanged(selectedPolicy.policyNumber);
    } else {
      this.selectedPolicy = null;
      this.claimForm.patchValue({ policyNumber: '' });
      this.policyError = 'No active policies are available for claim submission.';
    }
  }

  private loadFallbackActivePolicies(notice: string, preferredPolicyNumber?: string): void {
    this.policyMockService.getFallbackActivePolicies().subscribe({
      next: (activePolicies) => {
        if (activePolicies.length > 0) {
          this.policyNotice = notice;
          this.policyError = '';
          this.setSelectablePolicies(activePolicies, preferredPolicyNumber);
        } else {
          this.policies = [];
          this.selectedPolicy = null;
          this.claimForm.patchValue({ policyNumber: '' });
          this.policyError = 'No active policies are available for claim submission.';
        }
        this.isPolicyLoading = false;
      },
      error: () => {
        this.policies = [];
        this.selectedPolicy = null;
        this.claimForm.patchValue({ policyNumber: '' });
        this.policyError = 'Unable to load policy details.';
        this.isPolicyLoading = false;
      }
    });
  }

  private syncClaimAmountToCoverage(): void {
    const current = this.claimForm.controls.claimAmount.value || 0;
    const max = this.claimAmountMax;
    if (current <= 0) {
      this.claimForm.patchValue({ claimAmount: Math.min(max, 1000) });
      return;
    }
    if (current > max) {
      this.claimForm.patchValue({ claimAmount: max });
    }
  }
}
