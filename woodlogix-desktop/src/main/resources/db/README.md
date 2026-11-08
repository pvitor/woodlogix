# SQLite schema

Flyway applies `migration/V1__initial_sqlite_schema.sql` to a new SQLite database
before Hibernate validates the mappings. Hibernate does not update the schema.
Subsequent schema changes must use new versioned SQLite migrations; do not edit
an applied migration.

V1 is an initial schema for a new database, not an upgrade for existing data.
Flyway's default configuration rejects a nonempty database without its history
table. An upgrade requires a separate plan, including a consistent backup,
inspection of the actual schema and orphaned records, and a tested migration.
Do not enable automatic baselining or run V1 against an existing database as a
substitute for that plan.

SQLite enforces the declared foreign keys only when
`woodlogix.database.foreign-keys=true` on each connection. This is the default;
an external configuration can disable enforcement. Hibernate `validate` checks
the mapping against the schema but is not a substitute for checking physical
foreign keys. The integration tests inspect `PRAGMA foreign_key_list` and
exercise constraints against temporary migrated databases.

The migration is SQLite-specific. PostgreSQL support and its migrations have not
been implemented.
