import { AsyncPipe, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { RoleName } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { PaginatedResponse, StandardResponse } from '../../core/models/common.models';
import { ClaimResponse } from '../../core/models/claim.models';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgIf, AsyncPipe, RouterLink, ShellCardComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  totalClaims = 0;
  isLoading = false;
  isPolicyLoading = false;
  totalPolicies = 0;
  activePolicies = 0;
  expiredPolicies = 0;
  readonly user$ = this.authService.currentUser$;
  readonly headline$ = this.user$.pipe(
    map((user) => {
      if (!user) {
        return 'Operations Workspace';
      }
      if (user.roles.includes('ROLE_ADMIN')) {
        return 'Platform Administration';
      }
      if (user.roles.includes('ROLE_MANAGER')) {
        return 'Claims Governance Console';
      }
      if (user.roles.includes('ROLE_ADJUSTER')) {
        return 'Claims Assessment Desk';
      }
      return 'Policyholder Dashboard';
    })
  );

  constructor(
    private readonly authService: AuthService,
    private readonly claimsApiService: ClaimsApiService
  ) {
    this.loadSummaryCounts();
  }

  isRole(role: RoleName): boolean {
    return this.authService.hasRole(role);
  }

  formatRoles(roles: string[] | undefined): string {
    if (!roles || roles.length === 0) {
      return 'Unassigned';
    }
    return roles
      .map((role) =>
        role
          .replace(/^ROLE_/, '')
          .toLowerCase()
          .replace(/\b\w/g, (char) => char.toUpperCase())
      )
      .join(', ');
  }

  claimsPanelTitle(): string {
    if (this.isRole('ROLE_MANAGER')) {
      return 'Claims Governance';
    }
    if (this.isRole('ROLE_ADMIN')) {
      return 'Claims Oversight';
    }
    return 'Assigned Claims';
  }

  claimsPanelDescription(): string {
    if (this.isRole('ROLE_MANAGER')) {
      return 'Review queue, assign adjusters, and progress claim decisions.';
    }
    if (this.isRole('ROLE_ADMIN')) {
      return 'Monitor end-to-end claim execution and intervene when needed.';
    }
    return 'Review your assigned queue and move claims through assessment.';
  }

  canAccessSettlements(): boolean {
    return this.isRole('ROLE_MANAGER');
  }

  canAccessDocuments(): boolean {
    return this.isRole('ROLE_ADMIN');
  }

  documentsPanelDescription(): string {
    if (this.isRole('ROLE_MANAGER')) {
      return 'Review and download evidence attached to claim records.';
    }
    return 'Upload required evidence and retrieve files attached to claim records.';
  }

  private isAdjusterOnly(): boolean {
    return this.isRole('ROLE_ADJUSTER') && !this.isRole('ROLE_MANAGER') && !this.isRole('ROLE_ADMIN');
  }

  private loadSummaryCounts(): void {
    this.isLoading = true;
    if (this.authService.hasRole('ROLE_POLICYHOLDER')) {
      this.claimsApiService.getMyClaims().subscribe({
        next: (response: StandardResponse<ClaimResponse[]>) => {
          this.totalClaims = response.data.length;
          this.isLoading = false;
        },
        error: () => {
          this.totalClaims = 0;
          this.isLoading = false;
        }
      });
      this.loadPolicySummary();
      return;
    }

    if (this.isAdjusterOnly()) {
      this.claimsApiService.getAssignedClaims().subscribe({
        next: (response: StandardResponse<ClaimResponse[]>) => {
          this.totalClaims = response.data.length;
          this.isLoading = false;
        },
        error: () => {
          this.totalClaims = 0;
          this.isLoading = false;
        }
      });
      return;
    }

    this.claimsApiService.getClaims().subscribe({
      next: (response: StandardResponse<PaginatedResponse<ClaimResponse>>) => {
        this.totalClaims = response.data.totalElements;
        this.isLoading = false;
      },
      error: () => {
        this.totalClaims = 0;
        this.isLoading = false;
      }
    });
  }

  private loadPolicySummary(): void {
    this.isPolicyLoading = true;
    this.claimsApiService.getMyPolicies().subscribe({
      next: (response) => {
        this.totalPolicies = response.data.totalPolicies;
        this.activePolicies = response.data.activePolicies;
        this.expiredPolicies = response.data.expiredPolicies;
        this.isPolicyLoading = false;
      },
      error: () => {
        this.totalPolicies = 0;
        this.activePolicies = 0;
        this.expiredPolicies = 0;
        this.isPolicyLoading = false;
      }
    });
  }
}
