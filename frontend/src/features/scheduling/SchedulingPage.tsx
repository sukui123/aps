import { PageShell } from '../../shared/components/PageShell';

/** 排产任务 SC-01~08：参数/执行/进度(SSE)/摘要（本轮占位）。 */
export function SchedulingPage() {
  return (
    <PageShell
      title="排产任务"
      feature="SC-01~08"
      description="参数配置、发起排产、进度（SSE 订阅 scheduling_run）、结果摘要（原型 scheduling）"
    />
  );
}
