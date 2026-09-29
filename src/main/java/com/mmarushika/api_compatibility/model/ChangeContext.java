package com.mmarushika.api_compatibility.model;

/**
 * The part of an operation a change belongs to.
 *
 * <p>The context is what allows a change to be classified semantically instead of
 * merely structurally: a {@code required} flag flipping from {@code false} to
 * {@code true} is breaking in {@link #REQUEST} context (clients must now send the
 * value) but not in {@link #RESPONSE} context (clients simply receive a value they
 * may not have expected).</p>
 */
public enum ChangeContext {

    /** The change is about a path itself (added/removed). */
    PATH,

    /** The change is about an HTTP operation of a path (added/removed). */
    OPERATION,

    /** The change concerns what a client sends (parameters, request body, request schema). */
    REQUEST,

    /** The change concerns what a client receives (responses, response schema). */
    RESPONSE
}
