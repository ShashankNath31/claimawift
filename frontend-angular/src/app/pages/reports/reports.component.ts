import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { AdjusterPerformanceReport, ClaimSummaryReport, PaymentReport } from '../../core/models/report.models';
import { AuthService } from '../../core/services/auth.service';
import { ReportsApiService } from '../../core/services/reports-api.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';
import { StatTileComponent } from '../../ui/stat-tile/stat-tile.component';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    NgIf,
    NgFor,
    ReactiveFormsModule,
    DecimalPipe,
    DatePipe,
    ShellCardComponent,
    StatTileComponent
  ],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss'
})
export class ReportsComponent {
  readonly filterForm = this.fb.nonNullable.group({
    startDate: [''],
    endDate: ['']
  });

  claimSummary: ClaimSummaryReport | null = null;
  paymentReport: PaymentReport | null = null;
  adjusterPerformance: AdjusterPerformanceReport | null = null;
  isLoading = false;
  isExportingClaimPdf = false;
  isExportingPaymentPdf = false;
  isExportingAdjusterPdf = false;
  error = '';

  constructor(
    private readonly fb: FormBuilder,
    private readonly reportsApiService: ReportsApiService,
    private readonly authService: AuthService
  ) {}

  get canViewAdjusterPerformance(): boolean {
    return this.authService.hasRole('ROLE_MANAGER') || this.authService.hasRole('ROLE_ADMIN');
  }

  get canViewPaymentAnalytics(): boolean {
    return this.authService.hasRole('ROLE_MANAGER') || this.authService.hasRole('ROLE_ADMIN');
  }

  get isAdjusterOnly(): boolean {
    return (
      this.authService.hasRole('ROLE_ADJUSTER') &&
      !this.authService.hasRole('ROLE_MANAGER') &&
      !this.authService.hasRole('ROLE_ADMIN')
    );
  }

  get canExportPdf(): boolean {
    return this.authService.hasRole('ROLE_MANAGER') || this.authService.hasRole('ROLE_ADMIN');
  }

  loadReports(): void {
    const { startDate, endDate } = this.filterForm.getRawValue();
    this.isLoading = true;
    this.error = '';

    const adjusterReport$ = this.canViewAdjusterPerformance
      ? this.reportsApiService.getAdjusterPerformance(startDate, endDate)
      : of(null);
    const paymentReport$ = this.canViewPaymentAnalytics ? this.reportsApiService.getPaymentReport(startDate, endDate) : of(null);

    forkJoin({
      claimSummaryResponse: this.reportsApiService.getClaimSummary(startDate, endDate),
      paymentResponse: paymentReport$,
      adjusterResponse: adjusterReport$
    }).subscribe({
      next: ({ claimSummaryResponse, paymentResponse, adjusterResponse }) => {
        this.claimSummary = claimSummaryResponse.data;
        this.paymentReport = paymentResponse?.data ?? null;
        this.adjusterPerformance = adjusterResponse?.data ?? null;
        this.isLoading = false;
      },
      error: (error) => this.handleError(error)
    });
  }

  exportClaimSummaryPdf(): void {
    if (!this.canExportPdf) {
      return;
    }
    const { startDate, endDate } = this.filterForm.getRawValue();
    this.isExportingClaimPdf = true;

    this.reportsApiService.downloadClaimSummaryPdf(startDate, endDate).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, this.buildFileName('claim-summary-report'));
        this.isExportingClaimPdf = false;
      },
      error: (error) => {
        this.isExportingClaimPdf = false;
        this.handleError(error, 'Unable to export claim summary PDF.');
      }
    });
  }

  exportPaymentPdf(): void {
    if (!this.canExportPdf) {
      return;
    }
    const { startDate, endDate } = this.filterForm.getRawValue();
    this.isExportingPaymentPdf = true;

    this.reportsApiService.downloadPaymentReportPdf(startDate, endDate).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, this.buildFileName('payment-report'));
        this.isExportingPaymentPdf = false;
      },
      error: (error) => {
        this.isExportingPaymentPdf = false;
        this.handleError(error, 'Unable to export payment PDF.');
      }
    });
  }

  exportAdjusterPdf(): void {
    if (!this.canExportPdf || !this.canViewAdjusterPerformance) {
      return;
    }
    const { startDate, endDate } = this.filterForm.getRawValue();
    this.isExportingAdjusterPdf = true;

    this.reportsApiService.downloadAdjusterPerformancePdf(startDate, endDate).subscribe({
      next: (blob) => {
        this.downloadBlob(blob, this.buildFileName('adjuster-performance-report'));
        this.isExportingAdjusterPdf = false;
      },
      error: (error) => {
        this.isExportingAdjusterPdf = false;
        this.handleError(error, 'Unable to export adjuster performance PDF.');
      }
    });
  }

  private buildFileName(prefix: string): string {
    const now = new Date();
    const dateStamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
      now.getDate()
    ).padStart(2, '0')}`;
    return `${prefix}-${dateStamp}.pdf`;
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private handleError(error: unknown, fallback = 'Unable to generate reports for the selected date range.'): void {
    this.error = toUserErrorMessage(error, fallback, 'Please verify the selected dates and try again.');
    this.isLoading = false;
  }
}
