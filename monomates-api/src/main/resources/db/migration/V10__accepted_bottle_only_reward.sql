UPDATE accepted_item_types
SET base_tokens = 0,
    updated_at = now()
WHERE code = 'CLEAR_PET_BOTTLE';
