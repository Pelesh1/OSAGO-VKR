-- Cleanup of legacy tables that are not used by current backend logic.
-- Before applying on production, make sure you have a fresh backup.

-- Reporting legacy
drop table if exists insurance.report_exports;
drop table if exists insurance.report_rows;
drop table if exists insurance.reports;

-- Legacy policy extras (not used in current flow)
drop table if exists insurance.policy_versions;
drop table if exists insurance.policy_drivers;
drop table if exists insurance.application_attachments;

-- Legacy RBAC tables (security now uses users.status)
drop table if exists insurance.user_roles;
drop table if exists insurance.roles;
