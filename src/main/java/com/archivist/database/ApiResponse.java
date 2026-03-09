package com.archivist.database;

public record ApiResponse(int statusCode, String body, boolean success) {}
