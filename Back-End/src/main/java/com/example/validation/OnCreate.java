package com.example.validation;

/**
 * Bean-validation group marker used to apply constraints only when an entity is
 * being created (e.g. registration) and not on partial updates (PATCH).
 */
public interface OnCreate {
}
