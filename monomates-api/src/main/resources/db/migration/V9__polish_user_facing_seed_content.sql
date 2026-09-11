UPDATE app_users
SET full_name = CASE email
  WHEN 'user@monomates.local' THEN 'MonoMates Member'
  WHEN 'demo@monomates.app' THEN 'MonoMates Demo'
  WHEN 'operator@monomates.local' THEN 'Bin Operations'
  WHEN 'linh.demo@monomates.local' THEN 'Nguyen Minh Linh'
  WHEN 'nam.demo@monomates.local' THEN 'Tran Hoang Nam'
  WHEN 'mai.demo@monomates.local' THEN 'Le Thanh Mai'
  ELSE full_name
END,
updated_at = now()
WHERE email IN (
  'user@monomates.local',
  'demo@monomates.app',
  'operator@monomates.local',
  'linh.demo@monomates.local',
  'nam.demo@monomates.local',
  'mai.demo@monomates.local'
);

UPDATE accepted_item_types
SET description = 'Empty, clear PET bottle with no liquid or visible residue.',
    updated_at = now()
WHERE code = 'CLEAR_PET_BOTTLE'
  AND description = 'Empty, clear PET bottle accepted by the MonoMates pilot.';

UPDATE locations
SET name = 'District 10 Community Center',
    updated_at = now()
WHERE name = 'District 10 Community Pilot Site';

UPDATE vouchers
SET partner_name = 'HCMUT Campus Cafe',
    description = 'One regular hot or iced coffee at a participating campus cafe.',
    updated_at = now()
WHERE title = 'Coffee voucher'
  AND partner_name = 'HCMUT Campus Cafe (Demo)';

UPDATE vouchers
SET partner_name = 'HCMC Bus',
    description = 'Redeem this reward for one eligible public-transport fare.',
    updated_at = now()
WHERE title = 'Bus fare voucher'
  AND partner_name = 'HCMC Bus Partner (Demo)';

UPDATE vouchers
SET partner_name = 'Eco Market',
    description = 'Use this reward toward reusable or low-waste goods.',
    updated_at = now()
WHERE title = 'Eco market voucher'
  AND partner_name = 'Eco Market Partner (Demo)';
