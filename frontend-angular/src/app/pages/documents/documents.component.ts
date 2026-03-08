import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DocumentType, DocumentResponse } from '../../core/models/document.models';
import { ClaimReferenceService } from '../../core/services/claim-reference.service';
import { DocumentsApiService } from '../../core/services/documents-api.service';
import { AuthService } from '../../core/services/auth.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, DatePipe, DecimalPipe, ShellCardComponent],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss'
})
export class DocumentsComponent {
  readonly uploadForm = this.fb.nonNullable.group({
    claimReference: ['', [Validators.required]],
    documentType: ['CLAIM_FORM' as DocumentType, [Validators.required]],
    description: ['']
  });

  readonly filterForm = this.fb.nonNullable.group({
    claimReference: ['', [Validators.required]]
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

  selectedFile: File | null = null;
  documents: DocumentResponse[] = [];
  isLoading = false;
  isSubmitting = false;
  error = '';
  success = '';
  resolvedClaimNumber = '';
  resolvedClaimId: number | null = null;
  serverValidationErrors: Record<string, string> = {};

  constructor(
    private readonly fb: FormBuilder,
    private readonly claimReferenceService: ClaimReferenceService,
    private readonly documentsApiService: DocumentsApiService,
    private readonly authService: AuthService
  ) {}

  canUploadDocuments(): boolean {
    return this.authService.hasRole('ROLE_ADMIN');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  upload(): void {
    if (this.uploadForm.invalid || !this.selectedFile || this.isSubmitting) {
      this.uploadForm.markAllAsTouched();
      if (!this.selectedFile) {
        this.error = 'Select a PDF file before submitting.';
      }
      return;
    }

    const { claimReference, documentType, description } = this.uploadForm.getRawValue();
    this.isSubmitting = true;
    this.error = '';
    this.success = '';
    this.resolvedClaimNumber = '';
    this.resolvedClaimId = null;
    this.serverValidationErrors = {};

    this.claimReferenceService.resolveClaim(claimReference).subscribe({
      next: (claim) => {
        this.resolvedClaimNumber = claim.claimNumber;
        this.resolvedClaimId = claim.id;
        this.documentsApiService.uploadDocument(this.selectedFile as File, claim.id, documentType, description).subscribe({
          next: () => {
            this.success = `Document uploaded successfully for claim ${claim.claimNumber}.`;
            this.isSubmitting = false;
            this.loadByClaim(claim.id);
          },
          error: (error: unknown) => {
            this.serverValidationErrors = extractValidationErrors(error);
            this.error = toUserErrorMessage(
              error,
              'Document upload could not be completed.',
              'Please review document details and enter valid values.'
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

  load(): void {
    if (this.filterForm.invalid) {
      this.filterForm.markAllAsTouched();
      return;
    }

    this.error = '';
    this.success = '';
    const { claimReference } = this.filterForm.getRawValue();
    this.claimReferenceService.resolveClaim(claimReference).subscribe({
      next: (claim) => {
        this.resolvedClaimNumber = claim.claimNumber;
        this.resolvedClaimId = claim.id;
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

  download(doc: DocumentResponse): void {
    this.documentsApiService.downloadDocument(doc.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = doc.originalFileName || doc.fileName;
        anchor.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.error = 'Document download failed. Please try again.';
      }
    });
  }

  fieldError(formName: 'upload' | 'filter', field: string, label: string): string {
    const form = formName === 'upload' ? this.uploadForm : this.filterForm;
    return getFieldError(form, field, label, this.serverValidationErrors);
  }

  private loadByClaim(claimId: number): void {
    this.isLoading = true;
    this.documentsApiService.getDocumentsByClaim(claimId).subscribe({
      next: (response: { data: DocumentResponse[] }) => {
        this.documents = response.data;
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load documents for this claim.');
        this.isLoading = false;
      }
    });
  }

  formatDocumentType(type: DocumentType): string {
    return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }
}
