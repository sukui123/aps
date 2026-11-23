/** 通用工具：时间轴映射 / 冲突预检等纯函数放此处。 */

/**
 * 生成 RFC 4122 v4 UUID（用作写请求的 Idempotency-Key）。
 *
 * 背景：`crypto.randomUUID()` 仅在安全上下文（HTTPS / localhost）可用。
 * 平台同源预览等以普通 HTTP 提供的页面里该 API 缺失，直接调用会抛
 * `TypeError: crypto.randomUUID is not a function`，导致登录等所有写请求
 * 在发出前即失败。这里优先用 randomUUID，不可用时回退到在非安全上下文
 * 同样可用的 `crypto.getRandomValues` 构造 v4 UUID；极端情况下再用
 * Math.random 兜底（幂等键只需进程内/时限内唯一，不用于安全用途）。
 */
export function uuidv4(): string {
  const c: Crypto | undefined = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined;

  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }

  if (c && typeof c.getRandomValues === 'function') {
    const bytes = new Uint8Array(16);
    c.getRandomValues(bytes);
    // version = 4, variant = RFC 4122 (10xx)
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === 'x' ? r : (r & 0x3f) | 0x80;
    return v.toString(16);
  });
}
