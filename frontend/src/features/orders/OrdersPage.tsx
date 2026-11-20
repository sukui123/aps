import { PageShell } from '../../shared/components/PageShell';

/** 订单池 OR-02/04/05：筛选/表格/批量/优先级（本轮占位）。 */
export function OrdersPage() {
  return (
    <PageShell
      title="订单池"
      feature="OR-02/04/05"
      description="按交期/状态/客户/优先级筛选，批量操作与优先级维护（原型 orders）"
    />
  );
}
