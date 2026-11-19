-- One Postgres container hosts all 7 microservice databases.
-- Each service owns exactly one database, so the production topology matches local development.
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE cart_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
