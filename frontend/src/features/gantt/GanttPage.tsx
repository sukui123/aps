import { Segmented, Space, Tag } from 'antd';
import { PageShell } from '../../shared/components/PageShell';
import { useGanttStore, type ZoomLevel } from '../../store/ganttStore';

/**
 * 计划甘特 GB-01~08：自研内核（虚拟滚动 + 视口裁剪 + DOM 块）后续迭代实现。
 * 本轮只搭目录与骨架，使用 Zustand 证明 UI 态依赖可用。
 */
export function GanttPage() {
  const zoom = useGanttStore((s) => s.zoom);
  const setZoom = useGanttStore((s) => s.setZoom);

  return (
    <PageShell
      title="计划甘特"
      feature="GB-01~08"
      description="资源泳道甘特、拖拽、冲突校验、锁定（原型 gantt；正式路由 /plan）"
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space>
          <span>时间轴缩放：</span>
          <Segmented<ZoomLevel>
            value={zoom}
            onChange={setZoom}
            options={[
              { label: '日', value: 'day' },
              { label: '周', value: 'week' },
              { label: '月', value: 'month' },
            ]}
          />
          <Tag>当前：{zoom}</Tag>
        </Space>
        <div className="aps-placeholder">甘特画布骨架 · 虚拟滚动/拖拽/冲突校验后续迭代</div>
      </Space>
    </PageShell>
  );
}
