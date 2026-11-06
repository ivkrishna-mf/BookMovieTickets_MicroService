-- Database-per-service: each microservice owns its schema; no service reads another's tables.
CREATE DATABASE catalog_db;
CREATE DATABASE inventory_db;
CREATE DATABASE booking_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
