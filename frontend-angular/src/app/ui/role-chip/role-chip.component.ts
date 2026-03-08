import { Component, Input } from '@angular/core';
import { RoleName } from '../../core/models/auth.models';

@Component({
  selector: 'app-role-chip',
  standalone: true,
  templateUrl: './role-chip.component.html',
  styleUrl: './role-chip.component.scss'
})
export class RoleChipComponent {
  @Input({ required: true }) role!: RoleName;

  get text(): string {
    switch (this.role) {
      case 'ROLE_POLICYHOLDER':
        return 'Policyholder';
      case 'ROLE_ADJUSTER':
        return 'Adjuster';
      case 'ROLE_MANAGER':
        return 'Manager';
      case 'ROLE_ADMIN':
        return 'Administrator';
      default:
        return 'User';
    }
  }
}
