import { PageShell } from '../../shared/components/PageShell';

/** 未排清单 SC-08/AN-03：原因码分组/明细/建议（本轮占位）。 */
export function UnscheduledPage() {
  return (
    <PageShell
      title="未排清单"
      feature="SC-08/AN-03"
      description="原因码（MAT-001/RES-002 等）分组统计、订单下钻与建议处置（原型 unscheduled）"
    />
  );
}
