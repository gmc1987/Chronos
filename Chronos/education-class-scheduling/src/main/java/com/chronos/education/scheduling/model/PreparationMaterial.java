package com.chronos.education.scheduling.model;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @Table(name="edu_preparation_material")
public class PreparationMaterial { @Id @Column(length=64) private String id; @Column(name="preparation_id",nullable=false,length=64) private String preparationId; @Column(name="file_id",length=64) private String fileId; @Column(nullable=false,length=200) private String title; @Column(columnDefinition="text") private String metadataJson; }
