import { AsyncPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription, forkJoin, interval, map } from 'rxjs';
import { NotificationMessage } from './core/models/notification.models';
import { AuthService } from './core/services/auth.service';
import { NotificationsApiService } from './core/services/notifications-api.service';
import { ToastService } from './core/services/toast.service';
import { toUserErrorMessage } from './core/utils/error-message.util';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIf, NgFor, AsyncPipe, DatePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  readonly isAuthenticated$ = this.authService.currentUser$.pipe(map((user) => !!user));
  readonly currentUser$ = this.authService.currentUser$;
  readonly isAdmin$ = this.authService.currentUser$.pipe(map((user) => !!user?.roles?.includes('ROLE_ADMIN')));
  readonly isPolicyholder$ = this.authService.currentUser$.pipe(
    map((user) => !!user?.roles?.includes('ROLE_POLICYHOLDER'))
  );
  readonly isNonPolicyholder$ = this.authService.currentUser$.pipe(
    map((user) => !!user && !user.roles.includes('ROLE_POLICYHOLDER'))
  );
  readonly canSeeDocuments$ = this.authService.currentUser$.pipe(
    map((user) => !!user?.roles?.includes('ROLE_ADMIN'))
  );
  readonly canSeeReports$ = this.authService.currentUser$.pipe(
    map((user) => !!user?.roles?.some((role) => ['ROLE_ADJUSTER', 'ROLE_MANAGER', 'ROLE_ADMIN'].includes(role)))
  );
  readonly canAccessSettlements$ = this.authService.currentUser$.pipe(
    map((user) => !!user?.roles?.includes('ROLE_MANAGER'))
  );
  readonly toasts$ = this.toastService.toasts$;
  notifications: NotificationMessage[] = [];
  unreadNotificationCount = 0;
  isNotificationOpen = false;
  isNotificationLoading = false;
  notificationError = '';
  private authSubscription?: Subscription;
  private notificationsPollSubscription?: Subscription;

  constructor(
    private readonly authService: AuthService,
    private readonly toastService: ToastService,
    private readonly notificationsApiService: NotificationsApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.authSubscription = this.authService.currentUser$.subscribe((user) => {
      this.stopNotificationsPolling();
      if (!user) {
        this.resetNotificationsState();
        return;
      }
      this.loadNotifications();
      this.notificationsPollSubscription = interval(15000).subscribe(() => this.loadNotifications(true));
    });
  }

  ngOnDestroy(): void {
    this.authSubscription?.unsubscribe();
    this.stopNotificationsPolling();
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.resetNotificationsState();
        this.router.navigateByUrl('/');
      }
    });
  }

  toggleNotifications(): void {
    this.isNotificationOpen = !this.isNotificationOpen;
    if (this.isNotificationOpen && this.notifications.length === 0) {
      this.loadNotifications();
    }
  }

  loadNotifications(silent = false): void {
    if (!silent) {
      this.isNotificationLoading = true;
    }
    this.notificationError = '';
    forkJoin({
      notificationsResponse: this.notificationsApiService.getNotifications(),
      unreadCountResponse: this.notificationsApiService.getUnreadCount()
    }).subscribe({
      next: ({ notificationsResponse, unreadCountResponse }) => {
        this.notifications = notificationsResponse.data;
        this.unreadNotificationCount = unreadCountResponse.data.unreadCount ?? 0;
        this.isNotificationLoading = false;
      },
      error: (error: unknown) => {
        this.notificationError = toUserErrorMessage(error, 'Unable to load notifications.');
        this.isNotificationLoading = false;
      }
    });
  }

  markNotificationAsRead(notification: NotificationMessage, event?: Event): void {
    event?.stopPropagation();
    if (!notification.id || notification.isRead) {
      return;
    }
    this.notificationsApiService.markAsRead(notification.id).subscribe({
      next: () => {
        notification.isRead = true;
        this.unreadNotificationCount = Math.max(0, this.unreadNotificationCount - 1);
      },
      error: () => {
        // Ignore in-place mark-read errors and preserve current UI state.
      }
    });
  }

  markAllNotificationsAsRead(): void {
    if (!this.notifications.length) {
      return;
    }
    this.notificationsApiService.markAllAsRead().subscribe({
      next: () => {
        this.notifications = this.notifications.map((item) => ({ ...item, isRead: true }));
        this.unreadNotificationCount = 0;
      },
      error: (error: unknown) => {
        this.notificationError = toUserErrorMessage(error, 'Unable to mark notifications as read.');
      }
    });
  }

  openNotification(notification: NotificationMessage): void {
    this.markNotificationAsRead(notification);
    const targetUrl = this.resolveNotificationRoute(notification);
    this.isNotificationOpen = false;
    this.router.navigateByUrl(targetUrl);
  }

  dismissToast(id: number): void {
    this.toastService.dismiss(id);
  }

  private resolveNotificationRoute(notification: NotificationMessage): string {
    if (notification.actionUrl && notification.actionUrl.startsWith('/')) {
      return notification.actionUrl;
    }
    if (this.authService.hasRole('ROLE_POLICYHOLDER')) {
      return '/status';
    }
    return '/claims';
  }

  private stopNotificationsPolling(): void {
    this.notificationsPollSubscription?.unsubscribe();
    this.notificationsPollSubscription = undefined;
  }

  private resetNotificationsState(): void {
    this.notifications = [];
    this.unreadNotificationCount = 0;
    this.isNotificationOpen = false;
    this.isNotificationLoading = false;
    this.notificationError = '';
  }
}
