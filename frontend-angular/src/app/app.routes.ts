import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { AdminUsersComponent } from './pages/admin-users/admin-users.component';
import { ClaimsComponent } from './pages/claims/claims.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { DocumentsComponent } from './pages/documents/documents.component';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { PaymentsComponent } from './pages/payments/payments.component';
import { PoliciesComponent } from './pages/policies/policies.component';
import { PolicyholderClaimComponent } from './pages/policyholder-claim/policyholder-claim.component';
import { PolicyholderHistoryComponent } from './pages/policyholder-history/policyholder-history.component';
import { PolicyholderStatusComponent } from './pages/policyholder-status/policyholder-status.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { RegisterComponent } from './pages/register/register.component';
import { ReportsComponent } from './pages/reports/reports.component';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'register',
    component: RegisterComponent
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    component: DashboardComponent
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    component: ProfileComponent
  },
  {
    path: 'claims',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_ADJUSTER', 'ROLE_MANAGER', 'ROLE_ADMIN']
    },
    component: ClaimsComponent
  },
  {
    path: 'assessment/:id',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_ADJUSTER']
    },
    loadComponent: () => import('./pages/assessment/assessment.component').then((m) => m.AssessmentComponent)
  },
  {
    path: 'claim',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_POLICYHOLDER']
    },
    component: PolicyholderClaimComponent
  },
  {
    path: 'policies',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_POLICYHOLDER']
    },
    component: PoliciesComponent
  },
  {
    path: 'status',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_POLICYHOLDER']
    },
    component: PolicyholderStatusComponent
  },
  {
    path: 'history',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_POLICYHOLDER']
    },
    component: PolicyholderHistoryComponent
  },
  {
    path: 'documents',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_ADMIN']
    },
    component: DocumentsComponent
  },
  {
    path: 'payments',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_MANAGER']
    },
    component: PaymentsComponent
  },
  {
    path: 'reports',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_ADJUSTER', 'ROLE_MANAGER', 'ROLE_ADMIN']
    },
    component: ReportsComponent
  },
  {
    path: 'admin/users',
    canActivate: [authGuard, roleGuard],
    data: {
      roles: ['ROLE_ADMIN']
    },
    component: AdminUsersComponent
  },
  {
    path: '**',
    redirectTo: ''
  }
];
