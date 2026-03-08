import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-shell-card',
  standalone: true,
  imports: [NgClass],
  templateUrl: './shell-card.component.html',
  styleUrl: './shell-card.component.scss'
})
export class ShellCardComponent {
  @Input() variant: 'default' | 'accent' | 'outline' = 'default';
}
