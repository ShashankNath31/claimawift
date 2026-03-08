import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { map } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ShellCardComponent } from '../../ui/shell-card/shell-card.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [NgIf, NgFor, AsyncPipe, ShellCardComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent {
  readonly user$ = this.authService.currentUser$;
  readonly rolesText$ = this.user$.pipe(
    map((user) =>
      (user?.roles ?? [])
        .map((role) => role.replace('ROLE_', '').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase()))
        .join(', ')
    )
  );

  constructor(private readonly authService: AuthService) {}
}
