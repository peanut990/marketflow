CREATE DATABASE IF NOT EXISTS marketflow_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON marketflow_test.* TO 'marketflow'@'%';

FLUSH PRIVILEGES;
