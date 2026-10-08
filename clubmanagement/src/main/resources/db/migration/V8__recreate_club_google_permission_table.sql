-- ============================================================
-- V8: Drop old table schema with deprecated columns (can_create, can_delete, etc.)
--     and recreate club_google_permission cleanly for Sheet & Form
-- ============================================================

DROP TABLE IF EXISTS club_google_permission CASCADE;

CREATE TABLE club_google_permission (
    id SERIAL PRIMARY KEY,
    club_id INTEGER NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES Users(user_id) ON DELETE CASCADE,

    -- Quyền Google Sheet
    can_create_sheet BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete_sheet BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_sheet_title BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_sheet_data BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_sheet_type BOOLEAN NOT NULL DEFAULT FALSE,

    -- Quyền Google Form
    can_create_form BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete_form BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_form_title BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_form_data BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_form_type BOOLEAN NOT NULL DEFAULT FALSE,

    granted_by_user_id INTEGER REFERENCES Users(user_id) ON DELETE SET NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_cgp_club_user UNIQUE (club_id, user_id)
);

CREATE INDEX idx_cgp_club_user ON club_google_permission (club_id, user_id);
