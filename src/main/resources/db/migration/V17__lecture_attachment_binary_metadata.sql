alter table lecture_attachments add column storage_key varchar(255);
alter table lecture_attachments add column original_file_name varchar(255);
alter table lecture_attachments add column file_content_type varchar(127);
alter table lecture_attachments add column file_size_bytes bigint;

create index if not exists idx_lecture_attachments_storage_key on lecture_attachments(storage_key);
