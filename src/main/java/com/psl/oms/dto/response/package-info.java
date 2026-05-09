/**
 * Response DTOs — data transfer objects for outgoing API responses.
 *
 * These decouple the API surface from JPA entities, avoiding
 * LazyInitializationException, infinite recursion, and internal field leakage.
 * Each DTO has a static from(Entity) factory method for centralised mapping.
 */
package com.psl.oms.dto.response;
