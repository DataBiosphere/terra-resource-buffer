CREATE DATABASE testdb;
CREATE DATABASE testdb_stairway;
CREATE ROLE dbuser WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
CREATE ROLE dbuser_stairway WITH LOGIN ENCRYPTED PASSWORD 'dbpwd_stairway';

ALTER DATABASE testdb OWNER TO dbuser;
ALTER DATABASE testdb_stairway OWNER TO dbuser_stairway;