begin transaction;

create table schema_version (
   version text);

create table beta_notify (
   email text unique not null,
   environment text,
   features text);
commit;
