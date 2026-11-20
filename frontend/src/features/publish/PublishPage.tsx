import { PageShell } from '../../shared/components/PageShell';

/** 发布审批 PB-01~05：校验清单/审批流/下达（本轮占位）。 */
export function PublishPage() {
  return (
    <PageShell
      title="发布审批"
      feature="PB-01~05"
      description="发布前四项校验、版本冻结、审批流、工单下达 MES（原型 publish）"
    />
  );
}
