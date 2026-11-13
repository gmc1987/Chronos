ALTER TABLE fm_safety_incident ADD COLUMN organization_id varchar(64);

UPDATE fm_safety_incident incident
   SET organization_id = asset.organization_id
  FROM fm_asset asset
 WHERE incident.asset_id = asset.id;

UPDATE fm_safety_incident incident
   SET organization_id = repair.organization_id
  FROM fm_repair_order repair
 WHERE incident.organization_id IS NULL
   AND incident.repair_order_id = repair.id;

CREATE INDEX idx_fm_safety_incident_org ON fm_safety_incident (organization_id, occurred_at DESC);
