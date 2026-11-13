package com.chronos.facility.dao;

import com.chronos.facility.model.InventoryTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, String> {
	@Query("select t from InventoryTransaction t where t.materialId in (select m.id from MaintenanceMaterial m where m.organizationId in :ids) order by t.createTime desc")
	List<InventoryTransaction> findScopedTransactions(@Param("ids") java.util.Collection<String> ids);

	List<InventoryTransaction> findAllByOrderByCreateTimeDesc();
	Optional<InventoryTransaction> findByIdempotencyKey(String idempotencyKey);
}
