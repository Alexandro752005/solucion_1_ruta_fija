import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  selector: 'rf-organization-page',
  imports: [PageHeaderComponent],
  templateUrl: './organization.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrganizationPage {
  readonly session = inject(AuthSessionService);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organización asignada',
  );
}
