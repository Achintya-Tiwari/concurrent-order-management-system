package com.psl.oms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * DuplicateResourceException – thrown when an insert would violate a unique constraint.
 *
 * <p>Replaces the original project's silent "skip on duplicate" behaviour with
 * an explicit HTTP 409 Conflict — a more RESTful and debuggable approach.
 *
 * <p>Covers:
 * <ul>
 *   <li>Customer with an existing cell number</li>
 *   <li>StockItem with an existing name</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format(
            "%s already exists with %s: '%s'", resourceName, fieldName, fieldValue
        ));
    }
}
