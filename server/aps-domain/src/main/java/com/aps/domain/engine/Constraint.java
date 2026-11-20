package com.aps.domain.engine;

/** 约束接口骨架；P0 固定五类硬约束，后续由 ConstraintRegistry 表驱动装载。 */
public interface Constraint {

    ConstraintLevel level();

    String code();

    String reasonCode();
}
