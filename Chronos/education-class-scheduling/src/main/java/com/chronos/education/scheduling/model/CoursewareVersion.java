package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity; import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_courseware_version")
public class CoursewareVersion extends BaseEntity {
 @Column(name="courseware_id",nullable=false) private String coursewareId;
 @Column(name="version_no",nullable=false) private Integer versionNo;
 @Column(name="file_id",nullable=false) private String fileId;
 @Column(name="metadata_json",columnDefinition="text") private String metadataJson;
 @Column(nullable=false) private String status="DRAFT";
 @Column(name="bind_state",nullable=false) private String bindState="PENDING_BIND";
 private java.time.Instant boundAt,publishedAt,archivedAt;
}
