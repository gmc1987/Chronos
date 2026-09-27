package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_admission_ticket")
@Getter @Setter
public class ExamAdmissionTicket extends BaseEntity {
	@Column(name="candidate_id", nullable=false, length=64) private String candidateId;
	@Column(name="published_version", nullable=false) private Integer publishedVersion;
	@Column(name="ticket_no", nullable=false, length=64) private String ticketNo;
	@Column(name="seat_snapshot_json", nullable=false, columnDefinition="text") private String seatSnapshotJson;
	@Column(name="issued_at", nullable=false) private LocalDateTime issuedAt;
	@Column(name="revoked_at") private LocalDateTime revokedAt;
	@Column(nullable=false, length=24) private String status = "ISSUED";
}
