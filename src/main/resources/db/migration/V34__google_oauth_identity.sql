alter table app_users
    add column google_subject varchar(255);

create unique index uk_app_users_google_subject
    on app_users (google_subject);
