-- US-2.2 — optional page count for PDF score files (Epic 7, "Biblioteka utworów").
-- PDFBox supplies the value on upload; nullable column so:
--   * non-PDF rows (archives) stay NULL naturally, and
--   * a corrupted / password-protected PDF fails extraction gracefully to NULL
--     instead of blocking the upload.
ALTER TABLE score_files ADD COLUMN page_count INT NULL;
