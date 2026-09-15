package com.chronos.education.scheduling.model;
import com.chronos.model.pojo.BaseEntity; import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_teaching_material_version")
public class TeachingMaterialVersion extends BaseEntity {
 @Column(name="material_id",nullable=false) private String materialId;
 @Column(name="version_no",nullable=false) private Integer versionNo;
 @Column(name="file_id",nullable=false) private String fileId;
 @Column(name="metadata_json",columnDefinition="text") private String metadataJson;
 @Column(name="file_name",length=255) private String fileName;
 @Column(name="mime_type",length=128) private String mimeType;
 @Column(name="file_size") private Long fileSize;
 @Column(name="checksum_sha256",length=64) private String checksumSha256;
 @Column(name="uploaded_at") private java.time.Instant uploadedAt;
 @Column(nullable=false) private String status="DRAFT";
 @Column(name="bind_state",nullable=false) private String bindState="PENDING_BIND";
 private java.time.Instant boundAt,publishedAt,archivedAt;
}
