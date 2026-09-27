package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "t_user_portal_preference", uniqueConstraints = @UniqueConstraint(name = "uk_portal_preference_user", columnNames = "username"))
public class UserPortalPreference extends BaseEntity {
    @Column(name = "username", length = 100, nullable = false)
    private String username;
    // PostgreSQL stores this value as TEXT. @Lob makes Hibernate read it as an OID-backed CLOB.
    @Column(name = "layout_json", columnDefinition = "text", nullable = false)
    private String layoutJson;
    @Column(name = "theme", length = 30, nullable = false)
    private String theme = "LIGHT";
}
