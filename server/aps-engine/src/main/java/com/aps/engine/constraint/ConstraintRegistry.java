package com.aps.engine.constraint;

import com.aps.domain.engine.Constraint;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 约束注册表骨架（技术方案 §4.2/§9.3 SC-09）：P0 固定五类硬约束，P1 从 constraint_rule 表装载。
 */
@Component
public class ConstraintRegistry {

    /** 后续迭代：从 constraint_rule 读取启用状态/级别/参数，构造约束链。 */
    public List<Constraint> enabledConstraints() {
        return Collections.emptyList();
    }
}
