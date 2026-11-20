import { PageShell } from '../../shared/components/PageShell';

/** 工作台 AN-01：KPI 卡、异常提醒、最近批次（本轮占位）。 */
export function DashboardPage() {
  return (
    <PageShell
      title="工作台"
      feature="AN-01"
      description="待排/未排/负荷/准时率 KPI、异常提醒、最近排产批次（原型 dashboard）"
    />
  );
}
