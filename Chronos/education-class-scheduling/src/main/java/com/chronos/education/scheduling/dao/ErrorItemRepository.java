package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ErrorItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ErrorItemRepository extends JpaRepository<ErrorItem,String> {
	Optional<ErrorItem> findByBookIdAndSourceTypeAndSourceItemId(String bookId,String sourceType,String sourceItemId);
}
