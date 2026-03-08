import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentResponse, DocumentType } from '../models/document.models';
import { StandardResponse } from '../models/common.models';

@Injectable({
  providedIn: 'root'
})
export class DocumentsApiService {
  private readonly baseUrl = '/api/documents';

  constructor(private readonly http: HttpClient) {}

  uploadDocument(
    file: File,
    claimId: number,
    documentType: DocumentType,
    description: string
  ): Observable<StandardResponse<DocumentResponse>> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('claimId', String(claimId));
    formData.append('documentType', documentType);
    formData.append('description', description);
    return this.http.post<StandardResponse<DocumentResponse>>(`${this.baseUrl}/upload`, formData);
  }

  getDocumentsByClaim(claimId: number): Observable<StandardResponse<DocumentResponse[]>> {
    return this.http.get<StandardResponse<DocumentResponse[]>>(`${this.baseUrl}/claim/${claimId}`);
  }

  downloadDocument(documentId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${documentId}/download`, {
      responseType: 'blob'
    });
  }
}
