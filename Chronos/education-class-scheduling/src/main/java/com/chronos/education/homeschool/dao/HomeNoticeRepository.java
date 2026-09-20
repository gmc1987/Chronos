package com.chronos.education.homeschool.dao;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.homeschool.model.HomeNotice;
public interface HomeNoticeRepository extends JpaRepository<HomeNotice, String> {
	List<HomeNotice> findAllByOrderByCreateTimeDesc();
}
