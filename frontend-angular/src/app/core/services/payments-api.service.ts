import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StandardResponse } from '../models/common.models';
import { PaymentRequest, PaymentResponse } from '../models/payment.models';

@Injectable({
  providedIn: 'root'
})
export class PaymentsApiService {
  private readonly baseUrl = '/api/payments';

  constructor(private readonly http: HttpClient) {}

  createPayment(payload: PaymentRequest): Observable<StandardResponse<PaymentResponse>> {
    return this.http.post<StandardResponse<PaymentResponse>>(this.baseUrl, payload);
  }

  getPaymentsByClaim(claimId: number): Observable<StandardResponse<PaymentResponse[]>> {
    return this.http.get<StandardResponse<PaymentResponse[]>>(`${this.baseUrl}/claim/${claimId}`);
  }
}
