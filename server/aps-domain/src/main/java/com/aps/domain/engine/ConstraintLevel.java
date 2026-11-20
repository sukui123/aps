package com.aps.domain.engine;

/** 三级约束（技术方案 §4.2）：违反即不可行 / 违反计惩罚 / 影响排序选择。 */
public enum ConstraintLevel {
    HARD,
    SOFT,
    PREFERENCE
}
