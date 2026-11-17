import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { DashboardAdminPreviewComponent } from './preview/dashboard-admin-preview.component';

document.title = 'Ruta Fija | Maqueta Dashboard ADMIN';
bootstrapApplication(DashboardAdminPreviewComponent, { providers: [provideZonelessChangeDetection()] })
  .catch((error: unknown) => console.error('No se pudo abrir la maqueta aislada.', error));
