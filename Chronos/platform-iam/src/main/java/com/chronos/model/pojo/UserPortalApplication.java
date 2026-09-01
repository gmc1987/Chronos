package com.chronos.model.pojo;

import java.time.LocalDateTime;
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
@Table(name = "t_user_portal_application", uniqueConstraints = @UniqueConstraint(name = "uk_user_portal_app", columnNames = {"username", "application_id"}))
public class UserPortalApplication extends BaseEntity {
    @Column(name = "username", length = 100, nullable = false)
    private String username;
    @Column(name = "application_id", length = 64, nullable = false)
    private String applicationId;
    @Column(name = "favorite", nullable = false)
    private Boolean favorite = false;
    @Column(name = "favorite_order")
    private Integer favoriteOrder = 0;
    @Column(name = "last_visited_at")
    private LocalDateTime lastVisitedAt;
    @Column(name = "visit_count", nullable = false)
    private Long visitCount = 0L;
}
