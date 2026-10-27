-- BO SUNS INDEX CHO CAC BANG NONG CHUA CO INDEX (PostgreSQL khong tu tao index cho cot FK).
-- Dung DO block de bo qua bang chua ton tai (mot so bang do ddl-auto tao, khong qua Flyway),
-- nen migration an toan ca tren DB lan luot chay va DB moi.

DO $$
DECLARE
    t TEXT;
    idx RECORD;
BEGIN
    FOR idx IN SELECT * FROM (VALUES
        -- bang, ten index, dinh nghia cot
        ('club_member',            'idx_club_member_club_user',            'club_id, user_id'),
        ('club_member',            'idx_club_member_club_status',          'club_id, status'),
        ('club_member',            'idx_club_member_user_id',              'user_id'),
        ('club_member',            'idx_club_member_department_id',        'department_id'),
        ('department',             'idx_department_club_id',               'club_id'),
        ('club_event',             'idx_club_event_club_start',            'club_id, start_time'),
        ('club_event',             'idx_club_event_start_time',            'start_time'),
        ('club_event',             'idx_club_event_created_by',            'created_by'),
        ('club_tasks',             'idx_club_tasks_club_id',               'club_id'),
        ('club_tasks',             'idx_club_tasks_club_status',           'club_id, status'),
        ('club_tasks',             'idx_club_tasks_club_due',              'club_id, due_date'),
        ('club_tasks',             'idx_club_tasks_event_id',              'event_id'),
        ('club_tasks',             'idx_club_tasks_assigned_user',         'assigned_user_id'),
        ('financial_transactions', 'idx_fin_tx_club_status_type_date',     'club_id, status, type, created_at'),
        ('financial_transactions', 'idx_fin_tx_club_status',               'club_id, status'),
        ('club_document',          'idx_club_document_club_id',            'club_id'),
        ('club_document',          'idx_club_document_event_id',           'event_id'),
        ('club_document',          'idx_club_document_created_by',         'created_by'),
        ('club_document',          'idx_club_document_webhook',            'webhook_channel_id, webhook_resource_id'),
        ('document_revision',      'idx_document_revision_doc_version',    'club_document_id, version DESC'),
        ('fund_campaigns',         'idx_fund_campaigns_club_id',           'club_id'),
        ('fund_contributions',     'idx_fund_contributions_campaign_status','campaign_id, status'),
        ('fund_contributions',     'idx_fund_contributions_campaign_user', 'campaign_id, user_id'),
        ('fund_contributions',     'idx_fund_contributions_user_id',       'user_id'),
        ('google_account',         'idx_google_account_user_created',      'user_id, created_at DESC')
    ) AS x(tbl, idx_name, cols) LOOP
        IF to_regclass(idx.tbl) IS NOT NULL THEN
            t := format('CREATE INDEX IF NOT EXISTS %I ON %I (%s)', idx.idx_name, idx.tbl, idx.cols);
            EXECUTE t;
            RAISE NOTICE 'created index %', idx.idx_name;
        ELSE
            RAISE NOTICE 'skip index % - table % not yet present', idx.idx_name, idx.tbl;
        END IF;
    END LOOP;
END $$;
