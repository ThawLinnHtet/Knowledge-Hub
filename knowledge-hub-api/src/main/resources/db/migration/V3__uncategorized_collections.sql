UPDATE collections fallback
SET uncategorized = false,
    updated_at = now()
WHERE fallback.uncategorized
  AND lower(fallback.name) <> 'uncategorized'
  AND EXISTS (
      SELECT 1
      FROM collections named
      WHERE named.user_id = fallback.user_id
        AND lower(named.name) = 'uncategorized'
  );

UPDATE collections named
SET uncategorized = true,
    updated_at = now()
WHERE lower(named.name) = 'uncategorized'
  AND NOT named.uncategorized;

UPDATE collections fallback
SET name = 'Uncategorized',
    updated_at = now()
WHERE fallback.uncategorized
  AND lower(fallback.name) <> 'uncategorized';

INSERT INTO collections (user_id, name, uncategorized)
SELECT users.id, 'Uncategorized', true
FROM users
WHERE NOT EXISTS (
    SELECT 1
    FROM collections
    WHERE collections.user_id = users.id
      AND collections.uncategorized
);
