package com.lukksarna.skystarter.infrastructure.api.exception;

import java.net.URI;

public final class ProblemTypes {

    public static final URI VALIDATION = URI.create("urn:skystarter:error:validation");
    public static final URI NOT_FOUND = URI.create("urn:skystarter:error:not-found");
    public static final URI BAD_JSON = URI.create("urn:skystarter:error:bad-json");
    public static final URI ILLEGAL_ARGUMENT = URI.create("urn:skystarter:error:illegal-argument");
    public static final URI UNAUTHORIZED = URI.create("urn:skystarter:error:unauthorized");
    public static final URI ACCESS_DENIED = URI.create("urn:skystarter:error:access-denied");
    public static final URI CONFLICT = URI.create("urn:skystarter:error:conflict");
    public static final URI INTERNAL = URI.create("urn:skystarter:error:internal");

    private ProblemTypes() {
    }
}
