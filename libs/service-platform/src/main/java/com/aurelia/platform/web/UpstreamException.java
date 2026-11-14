package com.aurelia.platform.web;

/** A sibling service could not be reached or answered unexpectedly. */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message, Throwable cause) { super(message, cause); }
    public UpstreamException(String message) { super(message); }
}
