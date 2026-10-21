package com.chronos.facility.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.chronos.facility.model.MaintenanceLedgerEntry;

public interface MaintenanceLedgerRepository
		extends JpaRepository<MaintenanceLedgerEntry, String>, JpaSpecificationExecutor<MaintenanceLedgerEntry> {
}
