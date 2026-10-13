-- The container inventory screens are gone: finance records a container by typing its number when assigning it to a
-- paid investment, so its descriptive details become optional. Marketplace photos now belong to plans.

ALTER TABLE containers
    ALTER COLUMN condition DROP NOT NULL,
    ALTER COLUMN capacity_cbm DROP NOT NULL,
    ALTER COLUMN max_gross_kg DROP NOT NULL,
    ALTER COLUMN tare_kg DROP NOT NULL,
    ALTER COLUMN manufacture_year DROP NOT NULL,
    ALTER COLUMN current_location DROP NOT NULL,
    ALTER COLUMN location_country DROP NOT NULL;

CREATE TABLE plan_photos
(
    id          UUID PRIMARY KEY,
    product_id  UUID        NOT NULL REFERENCES investment_products (id),
    document_id UUID        NOT NULL REFERENCES stored_documents (id),
    uploaded_by UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT plan_photos_uk UNIQUE (product_id, document_id)
);
CREATE INDEX plan_photos_product_idx ON plan_photos (product_id, created_at);

-- Keep what the marketplace showed: up to six investor-visible photos of containers of each plan's type.
INSERT INTO plan_photos (id, product_id, document_id, uploaded_by, created_at)
SELECT gen_random_uuid(), product_id, document_id, uploaded_by, created_at
FROM (SELECT p.id AS product_id, d.document_id, d.uploaded_by, d.created_at,
             row_number() OVER (PARTITION BY p.id ORDER BY d.created_at, d.id) AS n
      FROM investment_products p
               JOIN containers c ON c.container_type = p.container_type
               JOIN container_documents d ON d.container_id = c.id
      WHERE d.purpose = 'CONTAINER_PHOTO' AND d.visible_to_investors) photos
WHERE n <= 6;
