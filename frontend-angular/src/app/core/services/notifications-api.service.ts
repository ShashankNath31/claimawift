import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StandardResponse } from '../models/common.models';
import { NotificationMessage } from '../models/notification.models';

@Injectable({
  providedIn: 'root'
})
export class NotificationsApiService {
  private readonly baseUrl = '/api/notifications';

  constructor(private readonly http: HttpClient) {}

  getNotifications(): Observable<StandardResponse<NotificationMessage[]>> {
    return this.http.get<StandardResponse<NotificationMessage[]>>(this.baseUrl);
  }

  getUnreadCount(): Observable<StandardResponse<{ unreadCount: number }>> {
    return this.http.get<StandardResponse<{ unreadCount: number }>>(`${this.baseUrl}/unread/count`);
  }

  markAsRead(notificationId: number): Observable<StandardResponse<void>> {
    return this.http.put<StandardResponse<void>>(`${this.baseUrl}/${notificationId}/read`, {});
  }

  markAllAsRead(): Observable<StandardResponse<void>> {
    return this.http.put<StandardResponse<void>>(`${this.baseUrl}/read-all`, {});
  }
}
