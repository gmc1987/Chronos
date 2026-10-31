package com.chronos.facility.dao;

import com.chronos.facility.model.InventoryTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, String> {
	List<InventoryTransaction> findAllByOrderByCreateTimeDesc();
	Optional<InventoryTransaction> findByIdempotencyKey(String idempotencyKey);
}
