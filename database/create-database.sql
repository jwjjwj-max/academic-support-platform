CREATE DATABASE IF NOT EXISTS academic_support
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'academic_support'@'localhost' IDENTIFIED BY '请替换为强密码';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON academic_support.* TO 'academic_support'@'localhost';
FLUSH PRIVILEGES;
