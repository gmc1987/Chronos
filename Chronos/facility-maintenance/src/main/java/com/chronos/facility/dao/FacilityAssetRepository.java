package com.chronos.facility.dao;

import com.chronos.facility.model.FacilityAsset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityAssetRepository extends JpaRepository<FacilityAsset, String> {
	List<FacilityAsset> findAllByOrderByAssetCode();
	Optional<FacilityAsset> findByAssetCode(String assetCode);
	Optional<FacilityAsset> findByQrToken(String qrToken);
}
