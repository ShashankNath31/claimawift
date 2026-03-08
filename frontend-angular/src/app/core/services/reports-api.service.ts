import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StandardResponse } from '../models/common.models';
import { AdjusterPerformanceReport, ClaimSummaryReport, PaymentReport } from '../models/report.models';

@Injectable({
  providedIn: 'root'
})
export class ReportsApiService {
  private readonly baseUrl = '/api/reports';

  constructor(private readonly http: HttpClient) {}

  getClaimSummary(startDate?: string, endDate?: string): Observable<StandardResponse<ClaimSummaryReport>> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get<StandardResponse<ClaimSummaryReport>>(`${this.baseUrl}/claims/summary${query}`);
  }

  getPaymentReport(startDate?: string, endDate?: string): Observable<StandardResponse<PaymentReport>> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get<StandardResponse<PaymentReport>>(`${this.baseUrl}/payments${query}`);
  }

  getAdjusterPerformance(
    startDate?: string,
    endDate?: string
  ): Observable<StandardResponse<AdjusterPerformanceReport>> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get<StandardResponse<AdjusterPerformanceReport>>(
      `${this.baseUrl}/adjusters/performance${query}`
    );
  }

  downloadClaimSummaryPdf(startDate?: string, endDate?: string): Observable<Blob> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get(`${this.baseUrl}/claims/summary/pdf${query}`, { responseType: 'blob' });
  }

  downloadPaymentReportPdf(startDate?: string, endDate?: string): Observable<Blob> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get(`${this.baseUrl}/payments/pdf${query}`, { responseType: 'blob' });
  }

  downloadAdjusterPerformancePdf(startDate?: string, endDate?: string): Observable<Blob> {
    const query = this.buildDateQuery(startDate, endDate);
    return this.http.get(`${this.baseUrl}/adjusters/performance/pdf${query}`, { responseType: 'blob' });
  }

  private buildDateQuery(startDate?: string, endDate?: string): string {
    const params = new URLSearchParams();
    if (startDate) {
      params.set('startDate', startDate);
    }
    if (endDate) {
      params.set('endDate', endDate);
    }
    const rawQuery = params.toString();
    return rawQuery ? `?${rawQuery}` : '';
  }
}
