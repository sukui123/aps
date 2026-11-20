package com.aps.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 资源时间轴工具。所有时间为相对计划起点的分钟数，区间为左闭右开 [start, end)。 */
public final class TimeSlots {
    private TimeSlots() {
    }

    public static List<long[]> buildWindows(int days, int[][] shifts) {
        List<long[]> windows = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            for (int[] shift : shifts) {
                windows.add(new long[]{(long) d * 1440 + shift[0] * 60L,
                                       (long) d * 1440 + shift[1] * 60L});
            }
        }
        return windows;
    }

    /** 起点推进到该资源最早的空闲时刻；无空闲返回 null。 */
    public static Long nextFreeStart(List<long[]> free, long earliest) {
        for (long[] iv : free) {
            if (iv[1] <= earliest) {
                continue;
            }
            return Math.max(iv[0], earliest);
        }
        return null;
    }

    /** 整段必须落在同一空闲区间内（用于换型）。 */
    public static Long peekSingle(List<long[]> free, long earliest, long dur) {
        for (long[] iv : free) {
            long s = Math.max(iv[0], earliest);
            if (s + dur <= iv[1]) {
                return s;
            }
        }
        return null;
    }

    /** 允许跨区间累计消耗（用于加工），返回分段占用；不修改 free。 */
    public static List<long[]> peekSpan(List<long[]> free, long earliest, long dur) {
        List<long[]> segs = new ArrayList<>();
        long remain = dur;
        long cur = earliest;
        for (long[] iv : free) {
            if (iv[1] <= cur) {
                continue;
            }
            long s = Math.max(iv[0], cur);
            if (s >= iv[1]) {
                continue;
            }
            long take = Math.min(iv[1] - s, remain);
            if (take > 0) {
                segs.add(new long[]{s, s + take});
                remain -= take;
                cur = s + take;
            }
            if (remain == 0) {
                return segs;
            }
        }
        return null;
    }

    /** 从空闲区间中切掉 [s,e)。 */
    public static void cut(List<long[]> free, long s, long e) {
        List<long[]> out = new ArrayList<>();
        for (long[] iv : free) {
            if (iv[1] <= s || iv[0] >= e) {
                out.add(iv);
            } else {
                if (iv[0] < s) {
                    out.add(new long[]{iv[0], s});
                }
                if (iv[1] > e) {
                    out.add(new long[]{e, iv[1]});
                }
            }
        }
        free.clear();
        free.addAll(out);
    }

    /** 回滚时把区间放回并合并相邻/重叠段。 */
    public static void mergeInsert(List<long[]> free, long s, long e) {
        free.add(new long[]{s, e});
        free.sort(Comparator.comparingLong(iv -> iv[0]));
        List<long[]> merged = new ArrayList<>();
        for (long[] iv : free) {
            if (!merged.isEmpty() && iv[0] <= merged.get(merged.size() - 1)[1]) {
                long[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], iv[1]);
            } else {
                merged.add(iv);
            }
        }
        free.clear();
        free.addAll(merged);
    }

    /** [s,e) 是否完全落在工作时段并集内。 */
    public static boolean covered(List<long[]> windows, long s, long e) {
        long cur = s;
        for (long[] w : windows) {
            if (w[1] <= cur) {
                continue;
            }
            if (w[0] > cur) {
                return false;
            }
            cur = Math.max(cur, w[1]);
            if (cur >= e) {
                return true;
            }
        }
        return cur >= e;
    }
}
