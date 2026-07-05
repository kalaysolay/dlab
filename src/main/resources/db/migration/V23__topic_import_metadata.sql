alter table topics add column imported boolean not null default false;
alter table topics add column import_note varchar(512);
alter table topics add column imported_at timestamp with time zone;
