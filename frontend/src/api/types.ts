/** 与技术方案 §5.1 对齐的统一返回体 / 分页结构 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp?: string;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface Resource {
  id?: number;
  tenantId?: number;
  code: string;
  name: string;
  type: string;
  parentId?: number | null;
  enabled?: boolean;
  version?: number;
}
