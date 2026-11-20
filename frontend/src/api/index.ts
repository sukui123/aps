import { request } from './client';
import type { PageResult, Resource } from './types';

/** 按域分组的接口封装骨架（orders/master/scheduling/plans/analysis 后续填充）。 */
export const masterApi = {
  listResources: (page = 1, pageSize = 20) =>
    request<PageResult<Resource>>({ url: '/master/resources', method: 'GET', params: { page, pageSize } }),

  getResource: (id: number) =>
    request<Resource>({ url: `/master/resources/${id}`, method: 'GET' }),
};

export const schedulingApi = {
  // POST /scheduling/runs（发起排产）、GET /scheduling/runs/{id}（进度）后续迭代接入
};

export const plansApi = {
  // GET/PATCH /plans/{id}/tasks、validate/publish/dispatch 后续迭代接入
};

export const analysisApi = {
  // GET /analysis/load、/analysis/unscheduled 后续迭代接入
};

export { request, toErrorMessage } from './client';
export type { ApiResult, PageResult, Resource } from './types';
