CREATE DATABASE testdb;
CREATE DATABASE testdb_stairway;
CREATE ROLE dbuser WITH LOGIN ENCRYPTED PASSWORD 'dbpwd';
CREATE ROLE dbuser_stairway WITH LOGIN ENCRYPTED PASSWORD 'dbpwd_stairway';

GRANT USAGE ON SCHEMA testdb.public TO dbuser;
GRANT CREATE ON SCHEMA testdb.public TO dbuser;

GRANT USAGE ON SCHEMA testdb_stairway.public TO dbuser_stairway;
GRANT CREATE ON SCHEMA testdb_stairway.public TO dbuser_stairway;
