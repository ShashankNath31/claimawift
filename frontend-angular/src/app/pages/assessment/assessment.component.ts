import { DatePipe, DecimalPipe, NgClass, NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdjustmentResponse,
  AdjustmentType,
  AssessmentDecision,
  AssessmentResponse,
  DecisionRequest
} from '../../core/models/assessment.models';
import { ClaimAuditEvent, ClaimResponse } from '../../core/models/claim.models';
import { DocumentResponse, DocumentType } from '../../core/models/document.models';
import { AssessmentApiService } from '../../core/services/assessment-api.service';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { DocumentsApiService } from '../../core/services/documents-api.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-assessment',
  standalone: true,
  imports: [NgIf, NgFor, NgClass, ReactiveFormsModule, DatePipe, DecimalPipe, RouterLink, ShellCardComponent],
  templateUrl: './assessment.component.html',
  styleUrl: './assessment.component.scss'
})
export class AssessmentComponent implements OnInit {
  readonly actionForm = this.fb.nonNullable.group({
    decision: ['APPROVED' as AssessmentDecision],
    approvedAmount: [0],
    adjustmentType: ['OTHER' as AdjustmentType],
    notes: ['']
  });

  readonly adjustmentTypes: AdjustmentType[] = [
    'DEPRECIATION_APPLIED',
    'EXCESS_DEDUCTED',
    'PART_DAMAGE_ONLY',
    'PRE_EXISTING_DAMAGE',
    'BETTERMENT_APPLIED',
    'POLICY_LIMIT_REACHED',
    'DUPLICATE_CLAIM',
    'INVESTIGATION_REQUIRED',
    'DOCUMENTATION_INCOMPLETE',
    'OTHER'
  ];

  claimId: number | null = null;
  claim: ClaimResponse | null = null;
  assessment: AssessmentResponse | null = null;
  adjustments: AdjustmentResponse[] = [];
  evidenceDocuments: DocumentResponse[] = [];
  auditTrail: ClaimAuditEvent[] = [];
  isLoading = false;
  isActionBusy = false;
  isEvidenceLoading = false;
  isAuditLoading = false;
  error = '';
  success = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly assessmentApiService: AssessmentApiService,
    private readonly claimsApiService: ClaimsApiService,
    private readonly documentsApiService: DocumentsApiService
  ) {}

  ngOnInit(): void {
    const claimIdParam = this.route.snapshot.paramMap.get('id');
    const parsedId = Number(claimIdParam);
    if (!Number.isFinite(parsedId) || parsedId <= 0) {
      this.error = 'Invalid claim reference for assessment.';
      return;
    }
    this.claimId = parsedId;
    this.loadAssessmentContext();
  }

  get canTakeAction(): boolean {
    return this.getDecisionOptions().length > 0;
  }

  getDecisionOptions(): AssessmentDecision[] {
    if (!this.claim) {
      return [];
    }
    if (['PAID', 'REJECTED', 'CANCELLED'].includes(this.claim.status)) {
      return [];
    }
    return ['APPROVED', 'REJECTED', 'ADJUSTED'];
  }

  shouldShowApprovedAmount(): boolean {
    const decision = this.actionForm.controls.decision.value;
    return decision === 'APPROVED' || decision === 'ADJUSTED';
  }

  shouldShowAdjustmentType(): boolean {
    return this.actionForm.controls.decision.value === 'ADJUSTED';
  }

  submitDecision(): void {
    if (!this.claim || !this.claimId || !this.assessment?.id || this.isActionBusy || !this.canTakeAction) {
      return;
    }

    const values = this.actionForm.getRawValue();
    if (!this.getDecisionOptions().includes(values.decision)) {
      this.error = `Invalid assessment decision for claim status ${this.formatStatus(this.claim.status)}.`;
      return;
    }

    this.isActionBusy = true;
    this.error = '';
    this.success = '';
    if (values.decision === 'ADJUSTED') {
      this.submitAdjustment(values.approvedAmount, values.adjustmentType, values.notes.trim());
      return;
    }

    const decisionRequest: DecisionRequest = {
      assessmentId: this.assessment.id,
      decision: values.decision,
      justification: values.notes.trim() || undefined
    };
    if (values.decision === 'APPROVED') {
      const resolvedAmount = this.resolveDecisionAmount(values.approvedAmount);
      if (resolvedAmount <= 0) {
        this.error = 'Approved amount must be greater than zero.';
        this.isActionBusy = false;
        return;
      }
      decisionRequest.finalAmount = resolvedAmount;
    }

    this.assessmentApiService.submitDecision(decisionRequest).subscribe({
      next: () => {
        this.success = `Assessment decision saved: ${this.formatStatus(values.decision)}.`;
        this.isActionBusy = false;
        this.loadAssessmentContext();
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to submit assessment decision.');
        this.isActionBusy = false;
      }
    });
  }

  requestManagerReview(): void {
    if (!this.claim || !this.claimId || !this.assessment?.id || this.isActionBusy || this.claim.status !== 'UNDER_REVIEW') {
      return;
    }

    const note = this.actionForm.controls.notes.value.trim();
    const payload: DecisionRequest = {
      assessmentId: this.assessment.id,
      decision: 'ADJUSTED',
      finalAmount: this.resolveDecisionAmount(this.actionForm.controls.approvedAmount.value),
      justification: note ? `MANAGER_REVIEW_REQUIRED: ${note}` : 'MANAGER_REVIEW_REQUIRED: Please review this claim.'
    };

    this.isActionBusy = true;
    this.error = '';
    this.success = '';
    this.assessmentApiService.submitDecision(payload).subscribe({
      next: () => {
        this.success = 'Claim moved for manager review.';
        this.isActionBusy = false;
        this.loadAssessmentContext();
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to request manager review.');
        this.isActionBusy = false;
      }
    });
  }

  canRequestManagerReview(): boolean {
    return this.claim?.status === 'UNDER_REVIEW';
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
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Document download failed.');
      }
    });
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatDocumentType(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatAuditAction(action: string): string {
    return action.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  private loadAssessmentContext(): void {
    if (!this.claimId) {
      return;
    }
    this.isLoading = true;
    this.error = '';
    this.success = '';

    this.claimsApiService.getClaimById(this.claimId).subscribe({
      next: (response) => {
        this.claim = response.data;
        this.loadOrCreateAssessment();
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim assessment details.');
        this.isLoading = false;
      }
    });

    this.loadEvidence();
    this.loadAuditTrail();
  }

  private loadOrCreateAssessment(): void {
    if (!this.claimId || !this.claim) {
      this.isLoading = false;
      return;
    }
    this.assessmentApiService.getAssessmentByClaim(this.claimId).subscribe({
      next: (response) => {
        this.applyAssessmentState(response.data);
        this.isLoading = false;
      },
      error: (error: unknown) => {
        if (!this.isMissingAssessmentError(error)) {
          this.error = toUserErrorMessage(error, 'Unable to load assessment details.');
          this.isLoading = false;
          return;
        }
        const currentClaim = this.claim;
        if (!currentClaim) {
          this.error = 'Claim details are unavailable for assessment initialization.';
          this.isLoading = false;
          return;
        }
        const seedAmount = currentClaim.claimAmount > 0 ? currentClaim.claimAmount : currentClaim.approvedAmount ?? 0;
        this.assessmentApiService
          .createAssessment({
            claimId: currentClaim.id,
            assessedAmount: seedAmount,
            justification: 'Initial assessment started from workspace.',
            notes: 'Assessment initialized.'
          })
          .subscribe({
            next: (createResponse) => {
              this.applyAssessmentState(createResponse.data);
              this.isLoading = false;
            },
            error: (createError: unknown) => {
              this.error = toUserErrorMessage(createError, 'Unable to initialize assessment record.');
              this.isLoading = false;
            }
          });
      }
    });
  }

  private applyAssessmentState(assessment: AssessmentResponse): void {
    this.assessment = assessment;
    this.adjustments = assessment.adjustments ?? [];
    const decisionOptions = this.getDecisionOptions();
    this.actionForm.patchValue({
      decision: decisionOptions[0] ?? 'APPROVED',
      approvedAmount: this.resolveDecisionAmount(assessment.recommendedAmount ?? assessment.assessedAmount ?? 0),
      adjustmentType: 'OTHER',
      notes: ''
    });
  }

  private submitAdjustment(amountInput: number, adjustmentType: AdjustmentType, notes: string): void {
    if (!this.assessment?.id || !this.claimId) {
      this.isActionBusy = false;
      return;
    }
    const adjustedAmount = this.resolveDecisionAmount(amountInput);
    if (adjustedAmount <= 0) {
      this.error = 'Adjusted amount must be greater than zero.';
      this.isActionBusy = false;
      return;
    }
    const reason = notes || 'Assessment amount adjusted by assessor.';
    this.assessmentApiService
      .addAdjustment({
        assessmentId: this.assessment.id,
        claimId: this.claimId,
        adjustedAmount,
        adjustmentType,
        reason,
        detailedNotes: notes || undefined
      })
      .subscribe({
        next: () => {
          this.success = 'Assessment adjustment recorded.';
          this.isActionBusy = false;
          this.loadAssessmentContext();
        },
        error: (error: unknown) => {
          this.error = toUserErrorMessage(error, 'Unable to submit assessment adjustment.');
          this.isActionBusy = false;
        }
      });
  }

  private resolveDecisionAmount(inputAmount: number): number {
    if (inputAmount > 0) {
      return inputAmount;
    }
    if (this.assessment?.recommendedAmount && this.assessment.recommendedAmount > 0) {
      return this.assessment.recommendedAmount;
    }
    if (this.assessment?.assessedAmount && this.assessment.assessedAmount > 0) {
      return this.assessment.assessedAmount;
    }
    if (this.claim?.approvedAmount && this.claim.approvedAmount > 0) {
      return this.claim.approvedAmount;
    }
    return this.claim?.claimAmount ?? 0;
  }

  private isMissingAssessmentError(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse)) {
      return false;
    }
    if (![400, 404].includes(error.status)) {
      return false;
    }
    const message = typeof error.error?.message === 'string' ? error.error.message : '';
    return message.toLowerCase().includes('assessment not found');
  }

  private loadEvidence(): void {
    if (!this.claimId) {
      return;
    }
    this.isEvidenceLoading = true;
    this.documentsApiService.getDocumentsByClaim(this.claimId).subscribe({
      next: (response) => {
        this.evidenceDocuments = response.data;
        this.isEvidenceLoading = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim evidence.');
        this.isEvidenceLoading = false;
      }
    });
  }

  private loadAuditTrail(): void {
    if (!this.claimId) {
      return;
    }
    this.isAuditLoading = true;
    this.claimsApiService.getClaimAudit(this.claimId).subscribe({
      next: (response) => {
        this.auditTrail = response.data;
        this.isAuditLoading = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim audit trail.');
        this.isAuditLoading = false;
      }
    });
  }
}
