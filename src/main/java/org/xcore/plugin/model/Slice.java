package org.xcore.plugin.model;

import java.util.List;

public record Slice<T>(List<T> items, boolean hasNext, AuditCursor nextCursor) {
}
