alter table lectures add column external_id varchar(128);

create unique index uq_lectures_external_id on lectures(external_id);
