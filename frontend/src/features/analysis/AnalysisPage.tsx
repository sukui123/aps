import * as echarts from 'echarts';
import { Alert, Tag } from 'antd';
import { PageShell } from '../../shared/components/PageShell';

/** 负荷分析 AN-02/05：负荷曲线与超载清单（本轮只导入 ECharts 验证依赖）。 */
export function AnalysisPage() {
  return (
    <PageShell
      title="负荷分析"
      feature="AN-02/05"
      description="资源/工作中心负荷曲线、瓶颈识别、超载清单与报表导出（原型 analysis）"
    >
      <Alert
        type="info"
        showIcon
        message={
          <span>
            ECharts 依赖可用（版本 <Tag color="blue">{echarts.version}</Tag>）· 图表渲染后续迭代
          </span>
        }
      />
    </PageShell>
  );
}
