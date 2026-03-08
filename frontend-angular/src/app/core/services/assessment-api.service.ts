import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AdjustmentRequest,
  AdjustmentResponse,
  AssessmentRequest,
  AssessmentResponse,
  DecisionRequest
} from '../models/assessment.models';
import { StandardResponse } from '../models/common.models';

@Injectable({
  providedIn: 'root'
})
export class AssessmentApiService {
  private readonly baseUrl = '/api/assessments';

  constructor(private readonly http: HttpClient) {}

  createAssessment(payload: AssessmentRequest): Observable<StandardResponse<AssessmentResponse>> {
    return this.http.post<StandardResponse<AssessmentResponse>>(this.baseUrl, payload);
  }

  getAssessmentByClaim(claimId: number): Observable<StandardResponse<AssessmentResponse>> {
    return this.http.get<StandardResponse<AssessmentResponse>>(`${this.baseUrl}/claim/${claimId}`);
  }

  getAssessmentById(assessmentId: number): Observable<StandardResponse<AssessmentResponse>> {
    return this.http.get<StandardResponse<AssessmentResponse>>(`${this.baseUrl}/${assessmentId}`);
  }

  getMyAssessments(): Observable<StandardResponse<AssessmentResponse[]>> {
    return this.http.get<StandardResponse<AssessmentResponse[]>>(`${this.baseUrl}/my-assessments`);
  }

  submitDecision(payload: DecisionRequest): Observable<StandardResponse<AssessmentResponse>> {
    return this.http.post<StandardResponse<AssessmentResponse>>(`${this.baseUrl}/decision`, payload);
  }

  addAdjustment(payload: AdjustmentRequest): Observable<StandardResponse<AdjustmentResponse>> {
    return this.http.post<StandardResponse<AdjustmentResponse>>(`${this.baseUrl}/adjustment`, payload);
  }

  getAdjustments(assessmentId: number): Observable<StandardResponse<AdjustmentResponse[]>> {
    return this.http.get<StandardResponse<AdjustmentResponse[]>>(`${this.baseUrl}/${assessmentId}/adjustments`);
  }
}
