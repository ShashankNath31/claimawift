import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RoleName, UserProfile } from '../../core/models/auth.models';
import { AdminApiService } from '../../core/services/admin-api.service';
import { extractValidationErrors, getFieldError, toUserErrorMessage } from '../../core/utils/error-message.util';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';
import { UserStatus } from '../../core/models/admin.models';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, ShellCardComponent],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss'
})
export class AdminUsersComponent implements OnInit {
  readonly createForm = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    phoneNumber: [''],
    role: ['ROLE_ADJUSTER' as RoleName, [Validators.required]]
  });

  readonly updateForm = this.fb.nonNullable.group({
    userId: [0, [Validators.required, Validators.min(1)]],
    role: ['ROLE_ADJUSTER' as RoleName, [Validators.required]],
    status: ['ACTIVE' as UserStatus, [Validators.required]]
  });

  readonly internalRoles: RoleName[] = ['ROLE_ADJUSTER', 'ROLE_MANAGER', 'ROLE_ADMIN'];
  readonly statuses: UserStatus[] = ['ACTIVE', 'INACTIVE', 'SUSPENDED'];
  users: UserProfile[] = [];
  error = '';
  success = '';
  isBusy = false;
  serverValidationErrors: Record<string, string> = {};

  constructor(
    private readonly fb: FormBuilder,
    private readonly adminApiService: AdminApiService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.adminApiService.getUsers().subscribe({
      next: (response) => {
        this.users = response.data;
      },
      error: (error: unknown) => {
        this.error = toUserErrorMessage(error, 'Unable to load user list.');
      }
    });
  }

  createUser(): void {
    if (this.createForm.invalid || this.isBusy) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.isBusy = true;
    this.error = '';
    this.success = '';
    this.serverValidationErrors = {};

    const values = this.createForm.getRawValue();
    this.adminApiService
      .createInternalUser({
        username: values.username,
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phoneNumber: values.phoneNumber,
        roles: [values.role]
      })
      .subscribe({
        next: () => {
          this.success = 'Internal account created successfully.';
          this.isBusy = false;
          this.loadUsers();
        },
        error: (error: unknown) => {
          this.serverValidationErrors = extractValidationErrors(error);
          this.error = toUserErrorMessage(
            error,
            'Internal account creation failed.',
            'Please review the user details and enter valid values.'
          );
          this.isBusy = false;
        }
      });
  }

  applyUpdates(): void {
    if (this.updateForm.invalid || this.isBusy) {
      this.updateForm.markAllAsTouched();
      return;
    }

    this.isBusy = true;
    this.error = '';
    this.success = '';
    this.serverValidationErrors = {};
    const values = this.updateForm.getRawValue();

    this.adminApiService.updateUserRoles(values.userId, { roles: [values.role] }).subscribe({
      next: () => {
        this.adminApiService.updateUserStatus(values.userId, { status: values.status }).subscribe({
          next: () => {
            this.success = 'User access role and status updated successfully.';
            this.isBusy = false;
            this.loadUsers();
          },
          error: (error: unknown) => {
            this.serverValidationErrors = extractValidationErrors(error);
            this.error = toUserErrorMessage(
              error,
              'Account status update failed.',
              'Please select valid role and status values.'
            );
            this.isBusy = false;
          }
        });
      },
      error: (error: unknown) => {
        this.serverValidationErrors = extractValidationErrors(error);
        this.error = toUserErrorMessage(
          error,
          'User role update failed.',
          'Please select valid role and status values.'
        );
        this.isBusy = false;
      }
    });
  }

  fieldError(formName: 'create' | 'update', field: string, label: string): string {
    const form = formName === 'create' ? this.createForm : this.updateForm;
    return getFieldError(form, field, label, this.serverValidationErrors);
  }

  formatRole(role: RoleName): string {
    return role.replace('ROLE_', '').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatStatus(status: UserStatus | string): string {
    return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
  }

  formatRoles(roles: RoleName[] | string[]): string {
    if (!roles || roles.length === 0) {
      return '-';
    }
    return roles.map((role) => this.formatRole(role as RoleName)).join(', ');
  }
}
