package com.psl.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer — a registered buyer in the OMS.
 *
 * Duplicate entries are prevented by the unique constraint on cell_number.
 * Deleting a customer cascades to their orders (DB FK + Hibernate cascade).
 */
@Entity
@Table(
    name = "customer",
    uniqueConstraints = @UniqueConstraint(name = "uq_customer_cell_number", columnNames = "cell_number")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "orders")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @NotBlank(message = "Customer name must not be blank")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Size(max = 255)
    @Column(name = "address")
    private String address;

    /** Mobile number — stored as String to preserve leading zeros. Must be unique. */
    @NotBlank(message = "Cell number must not be blank")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Cell number must be 10–15 digits")
    @Column(name = "cell_number", nullable = false, length = 15)
    private String cellNumber;

    /**
     * One-to-many with PurchaseOrder.
     * mappedBy = "customer" — FK lives on purchase_order table.
     * LAZY — orders are not loaded unless explicitly accessed.
     */
    @OneToMany(
        mappedBy = "customer",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PurchaseOrder> orders = new ArrayList<>();
}
