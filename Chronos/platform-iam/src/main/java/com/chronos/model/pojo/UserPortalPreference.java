package com.chronos.model.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
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
    @Lob
    @Column(name = "layout_json", columnDefinition = "text", nullable = false)
    private String layoutJson;
    @Column(name = "theme", length = 30, nullable = false)
    private String theme = "LIGHT";
}
