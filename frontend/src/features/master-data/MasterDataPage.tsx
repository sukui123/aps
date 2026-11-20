import { Tabs } from 'antd';
import { PageShell } from '../../shared/components/PageShell';

/** 主数据 MD-01~10：资源/工艺/日历/换型 四个 Tab（本轮占位）。 */
export function MasterDataPage() {
  return (
    <PageShell
      title="主数据"
      feature="MD-01~10"
      description="资源树、工艺路线、日历班次、换型矩阵四个 Tab（原型 master）"
    >
      <Tabs
        defaultActiveKey="resource"
        items={[
          { key: 'resource', label: '资源', children: <div className="aps-placeholder">资源树骨架</div> },
          { key: 'routing', label: '工艺', children: <div className="aps-placeholder">工艺路线骨架</div> },
          { key: 'calendar', label: '日历', children: <div className="aps-placeholder">日历班次骨架</div> },
          { key: 'setup', label: '换型', children: <div className="aps-placeholder">换型矩阵骨架</div> },
        ]}
      />
    </PageShell>
  );
}
