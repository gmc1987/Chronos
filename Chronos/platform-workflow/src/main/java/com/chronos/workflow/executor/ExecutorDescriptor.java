package com.chronos.workflow.executor;

public record ExecutorDescriptor(String code, String name, String nodeType, String description, boolean available) {
}
