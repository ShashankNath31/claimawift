import { DatePipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PolicyPortfolioResponse, PolicyResponse } from '../../core/models/policy.models';
import { ClaimsApiService } from '../../core/services/claims-api.service';
import { PolicyMockService } from '../../core/services/policy-mock.service';
import { ToastService } from '../../core/services/toast.service';
import { toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

type PolicyFilter = 'ALL' | 'ACTIVE' | 'EXPIRED' | 'EXPIRING_SOON';

@Component({
  selector: 'app-policies',
  standalone: true,
  imports: [NgIf, NgFor, FormsModule, DatePipe, DecimalPipe, ShellCardComponent],
  templateUrl: './policies.component.html',
  styleUrl: './policies.component.scss'
})
export class PoliciesComponent implements OnInit {
  allPolicies: PolicyResponse[] = [];
  filteredPolicies: PolicyResponse[] = [];
  totalPolicies = 0;
  activePolicies = 0;
  expiredPolicies = 0;
  isLoading = false;
  error = '';
  isUsingFallbackPolicies = false;

  searchQuery = '';
  selectedFilter: PolicyFilter = 'ALL';
  currentPage = 1;
  readonly pageSize = 6;

  constructor(
    private readonly claimsApiService: ClaimsApiService,
    private readonly policyMockService: PolicyMockService,
    private readonly toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadPolicies();
  }

  onRefreshPolicies(): void {
    this.loadPolicies(true);
  }

  loadPolicies(showSuccessToast = false): void {
    this.isLoading = true;
    this.error = '';
    this.isUsingFallbackPolicies = false;

    this.claimsApiService.getMyPolicies().subscribe({
      next: (response) => {
        const portfolio: PolicyPortfolioResponse = response.data;
        this.setPoliciesFromPortfolio(portfolio);
        if (showSuccessToast) {
          this.toastService.showSuccess('Policies refreshed.');
        }
        this.isLoading = false;
      },
      error: (error: unknown) => {
        const message = toUserErrorMessage(error, 'Unable to load policies.');
        this.error = message.toLowerCase().includes('temporarily unavailable')
          ? 'Claim service is temporarily unavailable. Showing fallback policies for now.'
          : `${message} Showing fallback policies for now.`;
        this.policyMockService.getFallbackPortfolio().subscribe({
          next: (portfolio) => {
            this.isUsingFallbackPolicies = true;
            this.setPoliciesFromPortfolio(portfolio);
            if (showSuccessToast) {
              this.toastService.showInfo('Showing fallback policy data because claim service is unavailable.');
            }
            this.isLoading = false;
          },
          error: () => {
            this.setPolicies([]);
            this.toastService.showError('Unable to load policies.');
            this.isLoading = false;
          }
        });
      }
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.trim().toLowerCase();
    this.filteredPolicies = this.allPolicies.filter((policy) => {
      const matchesSearch =
        !query ||
        policy.policyNumber.toLowerCase().includes(query) ||
        policy.policyType.toLowerCase().includes(query) ||
        policy.planName.toLowerCase().includes(query) ||
        policy.vehicleRegistration.toLowerCase().includes(query) ||
        policy.insurerName.toLowerCase().includes(query);

      if (!matchesSearch) {
        return false;
      }

      if (this.selectedFilter === 'ALL') {
        return true;
      }
      if (this.selectedFilter === 'ACTIVE') {
        return policy.status === 'ACTIVE';
      }
      if (this.selectedFilter === 'EXPIRED') {
        return policy.status === 'EXPIRED';
      }
      if (this.selectedFilter === 'EXPIRING_SOON') {
        return policy.status === 'ACTIVE' && this.daysToExpiry(policy.expiryDate) <= 30;
      }
      return true;
    });
    this.currentPage = 1;
  }

  renewalLabel(policy: PolicyResponse): string {
    const days = this.daysToExpiry(policy.expiryDate);
    if (policy.status === 'EXPIRED') {
      return 'Renewal overdue';
    }
    if (days <= 30) {
      return `Renew in ${days} day${days === 1 ? '' : 's'}`;
    }
    return 'In force';
  }

  rowTone(policy: PolicyResponse): 'expired' | 'renewal' | 'active' {
    const days = this.daysToExpiry(policy.expiryDate);
    if (policy.status === 'EXPIRED') {
      return 'expired';
    }
    if (days <= 30) {
      return 'renewal';
    }
    return 'active';
  }

  get pagedPolicies(): PolicyResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredPolicies.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredPolicies.length / this.pageSize));
  }

  goToPreviousPage(): void {
    this.currentPage = Math.max(1, this.currentPage - 1);
  }

  goToNextPage(): void {
    this.currentPage = Math.min(this.totalPages, this.currentPage + 1);
  }

  private daysToExpiry(expiryDate: string): number {
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const expiry = new Date(expiryDate);
    expiry.setHours(0, 0, 0, 0);
    const diffMs = expiry.getTime() - now.getTime();
    return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  }

  private setPoliciesFromPortfolio(portfolio: PolicyPortfolioResponse): void {
    const policies = Array.isArray(portfolio?.policies) ? portfolio.policies : [];
    this.setPolicies(policies);
    if (typeof portfolio?.totalPolicies === 'number') {
      this.totalPolicies = portfolio.totalPolicies;
    }
    if (typeof portfolio?.activePolicies === 'number') {
      this.activePolicies = portfolio.activePolicies;
    }
    if (typeof portfolio?.expiredPolicies === 'number') {
      this.expiredPolicies = portfolio.expiredPolicies;
    }
  }

  private setPolicies(policies: PolicyResponse[]): void {
    this.allPolicies = [...policies].sort(
      (a, b) => new Date(a.expiryDate).getTime() - new Date(b.expiryDate).getTime()
    );
    this.totalPolicies = policies.length;
    this.activePolicies = policies.filter((policy) => policy.status === 'ACTIVE').length;
    this.expiredPolicies = policies.filter((policy) => policy.status === 'EXPIRED').length;
    this.applyFilters();
  }
}
