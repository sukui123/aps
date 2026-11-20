package com.aps.engine.model;

import java.util.List;

public record EngineResult(List<Task> tasks, List<Unscheduled> unscheduled) {
}
