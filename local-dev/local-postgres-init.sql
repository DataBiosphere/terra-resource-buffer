CREATE ROLE dbuser WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
CREATE ROLE dbuser_stairway WITH LOGIN ENCRYPTED PASSWORD 'dbpwd_stairway';

CREATE DATABASE testdb OWNER dbuser;
CREATE DATABASE testdb_stairway OWNER dbuser_stairway;
