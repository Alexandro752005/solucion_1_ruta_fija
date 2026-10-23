export interface ServiceReportTotals {
  readonly scheduled: number; readonly completed: number; readonly cancelled: number;
  readonly interrupted: number; readonly pending: number; readonly transferred: number;
  readonly completionPercent: number | null;
}
export interface ServiceReportItem {
  readonly serviceId: string; readonly serviceVersion: number;
  readonly cohortScheduledAt: string; readonly currentScheduledAt: string;
  readonly status: string; readonly validCompletion: boolean;
  readonly originalStartedAt: string | null; readonly originalEndedAt: string | null;
  readonly effectiveStartedAt: string | null; readonly effectiveEndedAt: string | null;
  readonly completionRecordedAt: string | null; readonly declaredClosed: boolean;
  readonly terminalResult: string | null; readonly correctionIds: readonly string[];
  readonly driverIds: readonly string[]; readonly vehicleIds: readonly string[]; readonly groupIds: readonly string[];
}
export interface ServiceReportView {
  readonly ruleVersion: string; readonly scope: string; readonly from: string; readonly to: string;
  readonly zoneId: string; readonly cutoffAt: string; readonly totals: ServiceReportTotals;
  readonly coverage: { readonly excludedLegacyOffers: number; readonly canonicalServicesWithoutFirstOffer: number; readonly notice: string };
  readonly items: readonly ServiceReportItem[];
}
export interface ServiceReportEmissionSummary {
  readonly id: string; readonly revision: number; readonly previousEmissionId: string | null;
  readonly issuedAt: string; readonly contentSha256: string;
}
export interface ServiceReportEmission extends ServiceReportEmissionSummary {
  readonly seriesId: string; readonly report: ServiceReportView;
}
export interface IssueServiceReport { readonly operationKey: string; readonly from: string; readonly to: string; }
export type ServiceReportExportFormat = 'csv' | 'xlsx' | 'pdf';
