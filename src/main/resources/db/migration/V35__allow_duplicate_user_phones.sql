-- V31 is already applied in production and must remain immutable. Phone uniqueness is
-- enforced by RegistrationService and ProfileService so historical shared family numbers
-- do not prevent the application from starting.
alter table app_users
    drop constraint if exists uq_app_users_phone;
