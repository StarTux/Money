package com.cavetale.money;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data @Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = { "owner" }))
public final class SQLAccount {
    @Id private Integer id;
    @Column(nullable = false) private UUID owner;
    @Column(nullable = false) private Double money;
}
