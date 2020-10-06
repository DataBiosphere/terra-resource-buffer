#!/bin/bash

# validate mysql
echo "sleeping for 5 seconds during postgres boot..."
sleep 5
export PGPASSWORD=dbpwd
psql --username dbuser --host=postgres --port=5432 -d testdb -c "SELECT VERSION();SELECT NOW()"
psql --username dbuser --host=postgres --port=5432 -d testdb -c "SELECT VERSION();SELECT NOW()"
