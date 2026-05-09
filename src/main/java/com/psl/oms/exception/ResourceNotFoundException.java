package com.psl.oms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ResourceNotFoundException – thrown when a requested entity does not exist in the DB.
 *
 * <p>{@code @ResponseStatus(HttpStatus.NOT_FOUND)} means Spring MVC will automatically
 * return HTTP 404 if this exception propagates out of a controller unhandled.
 * In Phase 2 we add a {@code GlobalExceptionHandler} that catches this and returns
 * a structured JSON error body instead of Spring's default error page.
 *
 * <p>Usage examples:
 * <pre>
 *   throw new ResourceNotFoundException("Customer", "id", customerId);
 *   // → "Customer not found with id: 42"
 *
 *   throw new ResourceNotFoundException("StockItem", "name", "Widget Pro");
 *   // → "StockItem not found with name: Widget Pro"
 * </pre>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    /**
     * Constructs a descriptive not-found exception.
     *
     * @param resourceName the type of entity (e.g. "Customer", "PurchaseOrder")
     * @param fieldName    the field that was searched (e.g. "id", "cellNumber")
     * @param fieldValue   the value that was not found
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName()    { return fieldName; }
    public Object getFieldValue()   { return fieldValue; }
}
