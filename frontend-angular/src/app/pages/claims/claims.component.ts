import { AsyncPipe, DatePipe, DecimalPipe, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AdminApiService } from '../../core/services/admin-api.service';
import {
  ClaimAuditEvent,
  ClaimResponse,
  ClaimStatus,
  ClaimStatusUpdateRequest
} from '../../core/models/claim.models';
import { StandardResponse, PaginatedResponse } from '../../core/models/common.models';
import { DocumentResponse, DocumentType } from '../../core/models/document.models';
import { UserProfile } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { DocumentsApiService } from '../../core/services/documents-api.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { RoleChipComponent } from '../../ui/role-chip/role-chip.component';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

type ClaimFilterStatus = 'ALL' | ClaimStatus;

@Component({
  selector: 'app-claims',
  standalone: true,
  imports: [
    NgIf,
    NgFor,
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    NgClass,
    AsyncPipe,
    ShellCardComponent,
    RoleChipComponent
  ],
  templateUrl: './claims.component.html',
  styleUrl: './claims.component.scss'
})
export class ClaimsComponent implements OnInit {
  readonly filterForm = this.fb.nonNullable.group({
    query: [''],
    status: ['ALL' as ClaimFilterStatus]
  });
  readonly statusFilters: ClaimFilterStatus[] = [
    'ALL',
    'SUBMITTED',
    'UNDER_REVIEW',
    'ADJUSTED',
    'APPROVED',
    'REJECTED',
    'PAID',
    'PAYMENT_FAILED',
    'CANCELLED'
  ];
  readonly terminalStatuses: ClaimStatus[] = ['REJECTED', 'PAID', 'CANCELLED'];
  readonly statusTransitions: Record<ClaimStatus, ClaimStatus[]> = {
    SUBMITTED: ['UNDER_REVIEW', 'CANCELLED'],
    UNDER_REVIEW: ['APPROVED', 'REJECTED', 'ADJUSTED', 'CANCELLED'],
    ADJUSTED: ['APPROVED', 'REJECTED', 'CANCELLED'],
    APPROVED: ['PAID', 'PAYMENT_FAILED'],
    PAYMENT_FAILED: ['PAID', 'CANCELLED'],
    REJECTED: [],
    PAID: [],
    CANCELLED: []
  };
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
  adjusters: UserProfile[] = [];
  activeAssignmentsByAdjuster: Record<number, number> = {};
  auditTrailByClaim: Record<number, ClaimAuditEvent[]> = {};
  auditLoadingByClaim: Record<number, boolean> = {};
  documentsByClaim: Record<number, DocumentResponse[]> = {};
  documentsLoadingByClaim: Record<number, boolean> = {};
  expandedAuditClaimId: number | null = null;
  expandedEvidenceClaimId: number | null = null;
  selectedAssigneeByClaim: Record<number, string> = {};
  selectedStatusByClaim: Record<number, ClaimStatus> = {};
  notesByClaim: Record<number, string> = {};
  approvedAmountByClaim: Record<number, number> = {};
  assignmentClaimId: number | null = null;
  isLoading = false;
  isActionBusy = false;
  activeActionClaimId: number | null = null;
  error = '';
  success = '';
  readonly currentUser$ = this.authService.currentUser$;

  constructor(
    private readonly fb: FormBuilder,
    private readonly claimsApiService: ClaimsApiService,
    private readonly documentsApiService: DocumentsApiService,
    private readonly authService: AuthService,
    private readonly adminApiService: AdminApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    if (this.canAssignClaims) {
      this.loadAdjusters();
      this.loadAdjusterAvailabilitySnapshot();
    }
    if (this.isAdjusterOnly) {
      this.filterForm.patchValue({ status: 'UNDER_REVIEW' });
    }
    this.loadClaims();
  }

  get isAdjusterOnly(): boolean {
    return (
      this.authService.hasRole('ROLE_ADJUSTER') &&
      !this.authService.hasRole('ROLE_MANAGER') &&
      !this.authService.hasRole('ROLE_ADMIN')
    );
  }

  get isManagerOnly(): boolean {
    return this.authService.hasRole('ROLE_MANAGER') && !this.authService.hasRole('ROLE_ADMIN');
  }

  get canAssignClaims(): boolean {
    return this.authService.hasRole('ROLE_MANAGER') || this.authService.hasRole('ROLE_ADMIN');
  }

  get canUpdateClaimStatus(): boolean {
    return (
      this.authService.hasRole('ROLE_ADJUSTER') ||
      this.authService.hasRole('ROLE_MANAGER') ||
      this.authService.hasRole('ROLE_ADMIN')
    );
  }

  get assignmentClaim(): ClaimResponse | null {
    if (this.assignmentClaimId == null) {
      return null;
    }
    return this.claims.find((claim) => claim.id === this.assignmentClaimId) ?? null;
  }

  applyFilters(): void {
    this.loadClaims();
  }

  clearFilters(): void {
    this.filterForm.setValue({
      query: '',
      status: this.isAdjusterOnly ? 'UNDER_REVIEW' : 'ALL'
    });
    this.loadClaims();
  }

  loadClaims(): void {
    this.isLoading = true;
    this.error = '';
    this.success = '';

    if (this.canAssignClaims) {
      this.loadAdjusterAvailabilitySnapshot();
    }

    if (this.isAdjusterOnly) {
      this.claimsApiService.getAssignedClaims().subscribe({
        next: (response: StandardResponse<ClaimResponse[]>) => {
          const filtered = this.applyAdjusterFilters(response.data);
          this.handleClaimsLoaded(filtered);
        },
        error: (error: unknown) => {
          this.error = toUserErrorMessage(error, 'Unable to load assigned claims.');
          this.isLoading = false;
        }
      });
      return;
    }

    const { query, status } = this.filterForm.getRawValue();
    const queryText = query.trim();
    if (queryText.length > 0) {
      this.claimsApiService.searchClaims(queryText).subscribe({
        next: (response: StandardResponse<ClaimResponse[]>) => {
          const claims = status === 'ALL' ? response.data : response.data.filter((claim) => claim.status === status);
          this.handleClaimsLoaded(claims);
        },
        error: (error: unknown) => {
          this.error = toUserErrorMessage(error, 'Unable to load claims.');
          this.isLoading = false;
        }
      });
      return;
    }

    const statusFilter = status === 'ALL' ? undefined : status;
    this.claimsApiService.getClaims(0, 50, statusFilter).subscribe({
      next: (response: StandardResponse<PaginatedResponse<ClaimResponse>>) => {
        this.handleClaimsLoaded(response.data.content);
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claims.');
        this.isLoading = false;
      }
    });
  }

  onAssigneeChange(claimId: number, event: Event): void {
    const target = event.target as HTMLSelectElement;
    this.selectedAssigneeByClaim[claimId] = target.value;
  }

  onAssignmentClaimChange(event: Event): void {
    const target = event.target as HTMLSelectElement;
    const claimId = Number(target.value);
    this.assignmentClaimId = Number.isFinite(claimId) && claimId > 0 ? claimId : null;
  }

  onStatusChange(claimId: number, event: Event): void {
    const target = event.target as HTMLSelectElement;
    this.selectedStatusByClaim[claimId] = target.value as ClaimStatus;
  }

  onNotesChange(claimId: number, event: Event): void {
    const target = event.target as HTMLInputElement;
    this.notesByClaim[claimId] = target.value;
  }

  onApprovedAmountChange(claimId: number, event: Event): void {
    const target = event.target as HTMLInputElement;
    const value = Number(target.value);
    this.approvedAmountByClaim[claimId] = Number.isFinite(value) ? value : 0;
  }

  assignClaim(claim: ClaimResponse): void {
    if (!this.canAssignClaims || this.isRowBusy(claim.id)) {
      return;
    }
    if (!this.isClaimAssignable(claim)) {
      this.error = 'Only submitted claims can be assigned.';
      return;
    }
    const selectedAdjuster = Number(this.selectedAssigneeByClaim[claim.id]);
    if (!Number.isFinite(selectedAdjuster) || selectedAdjuster <= 0) {
      this.error = 'Select an adjuster before assigning this claim.';
      return;
    }
    const selectedAdjusterProfile = this.adjusters.find((adjuster) => adjuster.id === selectedAdjuster);
    if (!selectedAdjusterProfile) {
      this.error = 'Selected adjuster could not be found.';
      return;
    }
    if ((selectedAdjusterProfile.status || '').toUpperCase() !== 'ACTIVE') {
      this.error = `Selected adjuster ${selectedAdjusterProfile.username} is not available for assignment.`;
      return;
    }

    this.runClaimAction(
      claim.id,
      this.claimsApiService.assignClaim(claim.id, selectedAdjuster),
      `Claim ${claim.claimNumber} assigned successfully.`
    );
  }

  unassignClaim(claim: ClaimResponse): void {
    if (!this.canAssignClaims || this.isRowBusy(claim.id)) {
      return;
    }
    this.runClaimAction(
      claim.id,
      this.claimsApiService.unassignClaim(claim.id),
      `Claim ${claim.claimNumber} unassigned successfully.`
    );
  }

  updateClaimStatus(claim: ClaimResponse): void {
    if (!this.canUpdateClaimStatus || this.isRowBusy(claim.id)) {
      return;
    }

    const selectedStatus = this.selectedStatusByClaim[claim.id] ?? claim.status;
    if (!this.getNextStatuses(claim.status).includes(selectedStatus)) {
      this.error = `Invalid status transition from ${this.formatStatus(claim.status)} to ${this.formatStatus(selectedStatus)}.`;
      return;
    }

    const payload: ClaimStatusUpdateRequest = {
      status: selectedStatus
    };
    const note = this.notesByClaim[claim.id]?.trim();
    if (note) {
      payload.notes = note;
    }
    const approvedAmount = this.approvedAmountByClaim[claim.id];
    if ((selectedStatus === 'APPROVED' || selectedStatus === 'ADJUSTED') && approvedAmount > 0) {
      payload.approvedAmount = approvedAmount;
    }

    this.runClaimAction(
      claim.id,
      this.claimsApiService.updateClaimStatus(claim.id, payload),
      `Claim ${claim.claimNumber} updated to ${this.formatStatus(selectedStatus)}.`
    );
  }

  requestManagerReview(claim: ClaimResponse): void {
    if (!this.isAdjusterOnly || this.isRowBusy(claim.id) || claim.status !== 'UNDER_REVIEW') {
      return;
    }
    const detail = this.notesByClaim[claim.id]?.trim();
    const note = detail ? `MANAGER_REVIEW_REQUIRED: ${detail}` : 'MANAGER_REVIEW_REQUIRED: Please review this claim.';
    const payload: ClaimStatusUpdateRequest = {
      status: 'ADJUSTED',
      notes: note
    };
    this.runClaimAction(
      claim.id,
      this.claimsApiService.updateClaimStatus(claim.id, payload),
      `Claim ${claim.claimNumber} moved for manager review.`
    );
  }

  canRequestManagerReview(claim: ClaimResponse): boolean {
    return this.isAdjusterOnly && claim.status === 'UNDER_REVIEW';
  }

  openAssessment(claim: ClaimResponse): void {
    this.router.navigate(['/assessment', claim.id]);
  }

  isRowBusy(claimId: number): boolean {
    return this.isActionBusy && this.activeActionClaimId === claimId;
  }

  shouldShowApprovedAmount(claimId: number, claim: ClaimResponse): boolean {
    const status = this.selectedStatusByClaim[claimId] ?? claim.status;
    return status === 'APPROVED' || status === 'ADJUSTED';
  }

  getNextStatuses(currentStatus: ClaimStatus): ClaimStatus[] {
    const base = this.statusTransitions[currentStatus] ?? [];
    if (this.isAdjusterOnly) {
      return base.filter((status) => !['PAID', 'PAYMENT_FAILED', 'CANCELLED'].includes(status));
    }
    if (this.isManagerOnly) {
      return base.filter((status) => status !== 'PAID');
    }
    return base;
  }

  hasAnyTransition(claim: ClaimResponse): boolean {
    return this.getNextStatuses(claim.status).length > 0;
  }

  adjusterAssignedCount(): number {
    return this.claims.length;
  }

  adjusterPendingCount(): number {
    return this.claims.filter((claim) => claim.status === 'UNDER_REVIEW' || claim.status === 'ADJUSTED').length;
  }

  adjusterDecidedTodayCount(): number {
    return this.claims.filter((claim) => {
      if (!['APPROVED', 'REJECTED', 'ADJUSTED'].includes(claim.status)) {
        return false;
      }
      return this.isToday(claim.updatedAt ?? claim.createdAt);
    }).length;
  }

  submittedAgeHours(claim: ClaimResponse): number {
    const createdAt = new Date(claim.createdAt).getTime();
    if (!Number.isFinite(createdAt)) {
      return 0;
    }
    return Math.max(Math.floor((Date.now() - createdAt) / (1000 * 60 * 60)), 0);
  }

  lastUpdateAgeHours(claim: ClaimResponse): number {
    const reference = claim.updatedAt ? claim.updatedAt : claim.createdAt;
    const updatedAt = new Date(reference).getTime();
    if (!Number.isFinite(updatedAt)) {
      return 0;
    }
    return Math.max(Math.floor((Date.now() - updatedAt) / (1000 * 60 * 60)), 0);
  }

  formatHoursAsAge(hours: number): string {
    const days = Math.floor(hours / 24);
    const remainder = hours % 24;
    if (days > 0) {
      return `${days}d ${remainder}h`;
    }
    return `${remainder}h`;
  }

  slaLabel(claim: ClaimResponse): string {
    if (this.terminalStatuses.includes(claim.status)) {
      return 'Closed';
    }
    const hours = this.submittedAgeHours(claim);
    if (hours > 72) {
      return 'Breached';
    }
    if (hours > 24) {
      return 'At Risk';
    }
    return 'Within SLA';
  }

  slaClass(claim: ClaimResponse): string {
    if (this.terminalStatuses.includes(claim.status)) {
      return 'sla-closed';
    }
    const hours = this.submittedAgeHours(claim);
    if (hours > 72) {
      return 'sla-critical';
    }
    if (hours > 24) {
      return 'sla-warn';
    }
    return 'sla-ok';
  }

  toggleAuditTrail(claimId: number): void {
    if (this.expandedAuditClaimId === claimId) {
      this.expandedAuditClaimId = null;
      return;
    }
    this.expandedAuditClaimId = claimId;
    if (!this.auditTrailByClaim[claimId]) {
      this.loadAuditTrail(claimId);
    }
  }

  toggleEvidence(claimId: number): void {
    if (this.expandedEvidenceClaimId === claimId) {
      this.expandedEvidenceClaimId = null;
      return;
    }
    this.expandedEvidenceClaimId = claimId;
    if (!this.documentsByClaim[claimId]) {
      this.loadEvidence(claimId);
    }
  }

  isAuditLoading(claimId: number): boolean {
    return this.auditLoadingByClaim[claimId] ?? false;
  }

  isEvidenceLoading(claimId: number): boolean {
    return this.documentsLoadingByClaim[claimId] ?? false;
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

  formatAuditAction(action: string): string {
    return action.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatDocumentType(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  getAdjusterAvailability(adjuster: UserProfile): string {
    const status = (adjuster.status || '').toUpperCase();
    if (status !== 'ACTIVE') {
      return 'Unavailable';
    }
    const activeAssignments = this.activeAssignmentsByAdjuster[adjuster.id] ?? 0;
    if (activeAssignments >= 8) {
      return `Busy (${activeAssignments})`;
    }
    if (activeAssignments >= 4) {
      return `Moderate (${activeAssignments})`;
    }
    return `Available (${activeAssignments})`;
  }

  getAdjusterDisplay(claim: ClaimResponse): string {
    if (claim.adjusterName && claim.adjusterName.trim().length > 0) {
      return claim.adjusterName;
    }
    if (claim.assignedAdjusterId) {
      const matched = this.adjusters.find((adjuster) => adjuster.id === claim.assignedAdjusterId);
      if (matched?.username) {
        return `${matched.username} (#${matched.id})`;
      }
      return `Assigned (#${claim.assignedAdjusterId})`;
    }
    return 'Unassigned';
  }

  private loadAdjusters(): void {
    this.adminApiService.getAdjusters().subscribe({
      next: (response) => {
        this.adjusters = response.data;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load adjuster list.');
      }
    });
  }

  isClaimAssignable(claim: ClaimResponse): boolean {
    return claim.status === 'SUBMITTED';
  }

  private applyAdjusterFilters(claims: ClaimResponse[]): ClaimResponse[] {
    const { query, status } = this.filterForm.getRawValue();
    const text = query.trim().toLowerCase();
    return claims.filter((claim) => {
      const statusMatch = status === 'ALL' || claim.status === status;
      if (!statusMatch) {
        return false;
      }
      if (!text) {
        return true;
      }
      return this.matchesQuery(claim, text);
    });
  }

  private matchesQuery(claim: ClaimResponse, query: string): boolean {
    return [
      claim.claimNumber,
      claim.policyNumber,
      claim.policyholderName ?? '',
      claim.policyholderPhone ?? ''
    ]
      .join(' ')
      .toLowerCase()
      .includes(query);
  }

  private handleClaimsLoaded(claims: ClaimResponse[]): void {
    this.claims = this.sortClaimsForDisplay(claims);
    if (!this.canAssignClaims && Object.keys(this.activeAssignmentsByAdjuster).length === 0) {
      this.rebuildActiveAssignments(claims);
    }
    if (this.expandedAuditClaimId && !this.claims.some((claim) => claim.id === this.expandedAuditClaimId)) {
      this.expandedAuditClaimId = null;
    }
    if (this.expandedEvidenceClaimId && !this.claims.some((claim) => claim.id === this.expandedEvidenceClaimId)) {
      this.expandedEvidenceClaimId = null;
    }
    this.seedRowState(this.claims);
    this.seedAssignmentSelection();
    this.isLoading = false;
  }

  private sortClaimsForDisplay(claims: ClaimResponse[]): ClaimResponse[] {
    if (!this.isAdjusterOnly) {
      return [...claims].sort((left, right) => {
        const leftClosed = this.terminalStatuses.includes(left.status);
        const rightClosed = this.terminalStatuses.includes(right.status);
        if (leftClosed !== rightClosed) {
          return leftClosed ? 1 : -1;
        }
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
      });
    }

    return [...claims].sort((left, right) => {
      const rankDiff = this.adjusterPriorityRank(left) - this.adjusterPriorityRank(right);
      if (rankDiff !== 0) {
        return rankDiff;
      }

      const ageDiff = this.submittedAgeHours(right) - this.submittedAgeHours(left);
      if (ageDiff !== 0) {
        return ageDiff;
      }

      return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    });
  }

  private adjusterPriorityRank(claim: ClaimResponse): number {
    if (this.terminalStatuses.includes(claim.status)) {
      return 3;
    }
    const ageHours = this.submittedAgeHours(claim);
    if (ageHours > 72) {
      return 0;
    }
    if (ageHours > 24) {
      return 1;
    }
    return 2;
  }

  private seedRowState(claims: ClaimResponse[]): void {
    for (const claim of claims) {
      this.selectedAssigneeByClaim[claim.id] = claim.assignedAdjusterId ? String(claim.assignedAdjusterId) : '';
      const allowedNext = this.getNextStatuses(claim.status);
      this.selectedStatusByClaim[claim.id] = allowedNext[0] ?? claim.status;
      this.notesByClaim[claim.id] = this.notesByClaim[claim.id] ?? '';
      this.approvedAmountByClaim[claim.id] =
        this.approvedAmountByClaim[claim.id] ?? claim.approvedAmount ?? claim.claimAmount;
    }
  }

  private seedAssignmentSelection(): void {
    if (!this.canAssignClaims) {
      this.assignmentClaimId = null;
      return;
    }

    if (this.assignmentClaimId && this.claims.some((claim) => claim.id === this.assignmentClaimId)) {
      return;
    }

    const nextClaim = this.claims.find((claim) => !this.terminalStatuses.includes(claim.status));
    this.assignmentClaimId = nextClaim ? nextClaim.id : null;
  }

  private loadAuditTrail(claimId: number): void {
    this.auditLoadingByClaim[claimId] = true;
    this.claimsApiService.getClaimAudit(claimId).subscribe({
      next: (response) => {
        this.auditTrailByClaim[claimId] = response.data;
        this.auditLoadingByClaim[claimId] = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load claim audit trail.');
        this.auditLoadingByClaim[claimId] = false;
      }
    });
  }

  private loadEvidence(claimId: number): void {
    this.documentsLoadingByClaim[claimId] = true;
    this.documentsApiService.getDocumentsByClaim(claimId).subscribe({
      next: (response) => {
        this.documentsByClaim[claimId] = response.data;
        this.documentsLoadingByClaim[claimId] = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load evidence documents.');
        this.documentsLoadingByClaim[claimId] = false;
      }
    });
  }

  private runClaimAction(claimId: number, request$: Observable<unknown>, successMessage: string): void {
    this.isActionBusy = true;
    this.activeActionClaimId = claimId;
    this.error = '';
    this.success = '';
    request$.subscribe({
      next: () => {
        this.success = successMessage;
        this.isActionBusy = false;
        this.activeActionClaimId = null;
        if (this.canAssignClaims) {
          this.loadAdjusterAvailabilitySnapshot();
        }
        this.loadClaims();
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to update claim workflow.');
        this.isActionBusy = false;
        this.activeActionClaimId = null;
      }
    });
  }

  private isToday(dateValue: string): boolean {
    const date = new Date(dateValue);
    const now = new Date();
    return (
      date.getFullYear() === now.getFullYear() &&
      date.getMonth() === now.getMonth() &&
      date.getDate() === now.getDate()
    );
  }

  private loadAdjusterAvailabilitySnapshot(): void {
    if (!this.canAssignClaims) {
      return;
    }
    this.claimsApiService.getClaims(0, 500).subscribe({
      next: (response: StandardResponse<PaginatedResponse<ClaimResponse>>) => {
        this.rebuildActiveAssignments(response.data.content);
      },
      error: () => {
        this.rebuildActiveAssignments(this.claims);
      }
    });
  }

  private rebuildActiveAssignments(claims: ClaimResponse[]): void {
    const counts: Record<number, number> = {};
    for (const claim of claims) {
      if (!claim.assignedAdjusterId || this.terminalStatuses.includes(claim.status)) {
        continue;
      }
      counts[claim.assignedAdjusterId] = (counts[claim.assignedAdjusterId] ?? 0) + 1;
    }
    this.activeAssignmentsByAdjuster = counts;
  }
}
