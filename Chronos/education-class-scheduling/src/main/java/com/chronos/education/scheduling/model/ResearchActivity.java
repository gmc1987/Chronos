package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor
@Table(name="edu_research_activity")
public class ResearchActivity extends BaseEntity {
 @Column(name="group_id", nullable=false, length=64) private String groupId;
 @Column(nullable=false, length=200) private String title;
 @Column(nullable=false, length=24) private String status="DRAFT";
 @Column(name="activity_time") private LocalDateTime activityTime;
 @Column(columnDefinition="text") private String content;
 @Column(name="end_time") private LocalDateTime endTime;
 @Column(length=200) private String location;
 @Column(columnDefinition="text") private String agenda;
 @Column(name="organizer_id",length=64) private String organizerId;
 @Column(columnDefinition="text") private String minutes;
 @Column(name="cancel_reason",columnDefinition="text") private String cancelReason;
 @Version @Column(name="row_version",nullable=false) private long rowVersion;
 @Column(nullable=false) private boolean archived;
}
