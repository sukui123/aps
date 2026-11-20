import type { ReactNode } from 'react';
import { Card, Tag, Typography } from 'antd';

interface PageShellProps {
  title: string;
  feature: string;
  description: string;
  children?: ReactNode;
}

/** 页面骨架：统一标题 + 功能编号标签 + 占位区（导入 AntD 证明依赖可用）。 */
export function PageShell({ title, feature, description, children }: PageShellProps) {
  return (
    <div className="aps-page">
      <Typography.Title level={4} className="aps-page-title">
        {title} <Tag color="blue">{feature}</Tag>
      </Typography.Title>
      <Typography.Paragraph className="aps-page-desc">{description}</Typography.Paragraph>
      <Card size="small">
        {children ?? (
          <div className="aps-placeholder">
            页面骨架已就绪 · 业务实现见后续迭代（对齐技术方案与 P0 backlog {feature}）
          </div>
        )}
      </Card>
    </div>
  );
}
