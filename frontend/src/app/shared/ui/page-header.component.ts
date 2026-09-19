import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'rf-page-header',
  templateUrl: './page-header.component.html',
  styleUrl: './page-header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() description = '';
  @Input() context: string | null | undefined;
  @Input() eyebrow = '';
}
