package com.chronos.model.pojo;

import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_organization", comment = "机构表")
public class Organization extends BaseEntity {
	
	@Column(name = "organization_name", length = 200, nullable = false)
	private String organizationName;
	
	@Column(name = "org_code", length = 100, nullable = false, unique = true)
	private String orgCode;

	@Column(name = "organization_type", length = 32)
	private String organizationType = "HOSPITAL";

	@Column(name = "short_name", length = 100)
	private String shortName;

	@Column(name = "timezone", length = 64)
	private String timezone = "Asia/Shanghai";

	@Column(name = "status")
	private Integer status = 1;

	@Column(name = "sort_order")
	private Integer sortOrder = 0;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_org_id", nullable = true, referencedColumnName = "id")
	private Organization parentOrgId;
	
	@JsonIgnore
	@OneToMany(mappedBy = "parentOrgId", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<Organization> children;
	
	@Column(name = "description", length = 500, nullable = true)
	private String description;
	
	@Column(name = "mailing_address", length = 500, nullable = true)
	private String mailingAddress;

	@Column(name = "tel", length = 32, nullable = true)
	private String tel;
	
	@Column(name = "managerId", length = 100, nullable = true)
	private String organizationManager;
	
	@Column(name = "industries", length = 500, nullable = true)
	private String industries;
	
	@Column(name = "register_time", nullable = false)
	private LocalDateTime registerTime;

}
