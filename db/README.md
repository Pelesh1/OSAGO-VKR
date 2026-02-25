# DB Snapshot

## Source
- DB: `vkr`
- Schema: `insurance`
- Host: `localhost`
- Port: `5432`

## Files
- `schema.sql` - structure only (tables, indexes, types, constraints)
- `seed.sql` - test data snapshot
- `src/main/resources/db/migration/V1__init.sql` - Flyway baseline migration for clean databases

## Restore Into Existing DB
```powershell
psql -h localhost -p 5432 -U postgres -d vkr -f .\db\schema.sql
psql -h localhost -p 5432 -U postgres -d vkr -f .\db\seed.sql
```

## Restore Into New Test DB
```powershell
createdb -h localhost -p 5432 -U postgres vkr_restore_test
psql -h localhost -p 5432 -U postgres -d vkr_restore_test -f .\db\schema.sql
psql -h localhost -p 5432 -U postgres -d vkr_restore_test -f .\db\seed.sql
```

## Recreate Snapshot
```powershell
pg_dump -h localhost -p 5432 -U postgres -d vkr --schema=insurance --schema-only --no-owner --no-privileges --encoding=UTF8 > .\db\schema.sql
pg_dump -h localhost -p 5432 -U postgres -d vkr --schema=insurance --data-only --inserts --column-inserts --encoding=UTF8 > .\db\seed.sql
```

## Notes
- Project uses `spring.jpa.hibernate.ddl-auto=none`, so DB schema must exist before app start.
- Check `seed.sql` for sensitive data before sharing or committing.
- Flyway is enabled in app config with `baseline-on-migrate=true`:
  - clean DB: `V1__init.sql` is applied automatically;
  - existing non-empty DB: Flyway creates baseline and does not recreate existing objects.
