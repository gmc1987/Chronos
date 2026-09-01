package com.chronos.model.pojo;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "t_iam_employee", comment = "IAM员工档案")
public class Employee extends BaseEntity {
    @Column(name = "employee_code", length = 100, nullable = false, unique = true) private String employeeCode;
    @Column(name = "employee_name", length = 100, nullable = false) private String employeeName;
    @Column(name = "gender", length = 16) private String gender;
    @Column(name = "phone", length = 32) private String phone;
    @Column(name = "email", length = 200) private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "id_number_cipher", length = 500) private String idNumberCipher;
    @Column(name = "employment_status", length = 32, nullable = false) private String employmentStatus = "ACTIVE";
    @Column(name = "employee_type", length = 32, nullable = false) private String employeeType = "STAFF";
    @Column(name = "hire_date") private LocalDate hireDate;
    @Column(name = "leave_date") private LocalDate leaveDate;
}
