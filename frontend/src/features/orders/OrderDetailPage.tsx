import { Tag } from 'antd';
import { useParams } from 'react-router-dom';
import { PageShell } from '../../shared/components/PageShell';

/** 订单详情 OR-03/06：工艺展开/齐套/排产结果（本轮占位）。 */
export function OrderDetailPage() {
  const { id } = useParams();
  return (
    <PageShell
      title={`订单详情 #${id ?? '-'}`}
      feature="OR-03/06"
      description="基本信息、工艺展开、齐套状态、排产结果（原型 orderDetail）"
    >
      <div className="aps-placeholder">订单 <Tag color="blue">{id}</Tag> 详情骨架 · 业务实现后续迭代</div>
    </PageShell>
  );
}
