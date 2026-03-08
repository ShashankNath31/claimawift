import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-stat-tile',
  standalone: true,
  templateUrl: './stat-tile.component.html',
  styleUrl: './stat-tile.component.scss'
})
export class StatTileComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) value = '';
  @Input() tone: 'teal' | 'orange' | 'ink' = 'ink';
}
