package com.aps.engine.model;

/** 结构化未排原因，可直接落 plan_unscheduled。 */
public record Unscheduled(String order, String op, String reason, String detail) {
}
