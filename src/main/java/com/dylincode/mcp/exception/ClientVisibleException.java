package com.dylincode.mcp.exception;

import java.util.Map;

public class ClientVisibleException extends RuntimeException {
    public final Map<String, Object> details;
    public ClientVisibleException(String message) { this(message, Map.of()); }
    public ClientVisibleException(String message, Map<String, Object> details) { super(message); this.details = details; }
}
