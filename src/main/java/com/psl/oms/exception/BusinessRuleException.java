package com.psl.oms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * BusinessRuleException – thrown when a valid request violates a domain business rule.
 *
 * <p>Returns HTTP 422 Unprocessable Entity — the request was well-formed but cannot
 * be processed in the current state of the resource.
 *
 * <p>Examples:
 * <ul>
 *   <li>Trying to ship an order that is already SHIPPED</li>
 *   <li>Ordering a quantity that exceeds available stock</li>
 * </ul>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
