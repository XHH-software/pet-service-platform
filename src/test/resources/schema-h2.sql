-- 测试用 H2 结构（MODE=MySQL）。
-- 与 sql/schema.sql 保持同构：字段、唯一索引、外键关系一一对应，
-- 只去掉 MySQL 专有的注释与存储引擎语法，保证 mvn test 不依赖 Docker / MySQL。

DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS appointment_slot;
DROP TABLE IF EXISTS service_daily_quota;
DROP TABLE IF EXISTS appointment_item;
DROP TABLE IF EXISTS appointment;
DROP TABLE IF EXISTS schedule;
DROP TABLE IF EXISTS customer_address;
DROP TABLE IF EXISTS pet;
DROP TABLE IF EXISTS customer;
DROP TABLE IF EXISTS package_detail;
DROP TABLE IF EXISTS service_package;
DROP TABLE IF EXISTS service_item;
DROP TABLE IF EXISTS staff_specialty;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS shop_business_hour;
DROP TABLE IF EXISTS shop;

CREATE TABLE staff (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  avatar VARCHAR(500),
  phone VARCHAR(20) NOT NULL UNIQUE,
  position TINYINT NOT NULL,
  level TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE staff_specialty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_id BIGINT NOT NULL,
  service_item_id BIGINT NOT NULL
);
CREATE UNIQUE INDEX uk_staff_item ON staff_specialty (staff_id, service_item_id);

CREATE TABLE category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  type TINYINT NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE service_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  image VARCHAR(500),
  category_id BIGINT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  duration INT NOT NULL,
  pet_size_limit TINYINT NOT NULL DEFAULT 0,
  daily_limit INT NOT NULL DEFAULT 10,
  description VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE service_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  image VARCHAR(500),
  category_id BIGINT NOT NULL,
  original_price DECIMAL(10,2) NOT NULL,
  selling_price DECIMAL(10,2) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 5,
  description VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE package_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id BIGINT NOT NULL,
  service_item_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1
);

CREATE TABLE schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_id BIGINT NOT NULL,
  schedule_date DATE NOT NULL,
  start_time TIME,
  end_time TIME,
  status TINYINT NOT NULL DEFAULT 1,
  booked_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);
CREATE UNIQUE INDEX uk_staff_date ON schedule (staff_id, schedule_date);

CREATE TABLE customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid VARCHAR(100) UNIQUE,
  nickname VARCHAR(50),
  avatar VARCHAR(500),
  phone VARCHAR(20),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE pet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  avatar VARCHAR(500),
  species TINYINT NOT NULL,
  breed VARCHAR(50),
  gender TINYINT,
  weight DECIMAL(5,2),
  birthday DATE,
  neutered TINYINT DEFAULT 0,
  vaccine_expiry_date DATE,
  allergies VARCHAR(200),
  remark VARCHAR(500),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE customer_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  province VARCHAR(50),
  city VARCHAR(50),
  district VARCHAR(50),
  detail VARCHAR(200) NOT NULL,
  contact_name VARCHAR(50) NOT NULL,
  contact_phone VARCHAR(20) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE appointment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(20) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  pet_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  appointment_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  paid_amount DECIMAL(10,2) DEFAULT 0,
  pay_status TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  source TINYINT NOT NULL DEFAULT 1,
  cancel_reason VARCHAR(200),
  remark VARCHAR(500),
  pay_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);
CREATE INDEX idx_date_staff ON appointment (appointment_date, staff_id);
CREATE INDEX idx_appointment_customer ON appointment (customer_id);
CREATE INDEX idx_appointment_status ON appointment (status);

CREATE TABLE appointment_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  service_item_id BIGINT,
  package_id BIGINT,
  service_name VARCHAR(100) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  duration INT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  subtotal DECIMAL(10,2) NOT NULL
);
CREATE INDEX idx_appointment_item ON appointment_item (appointment_id);

CREATE TABLE service_daily_quota (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  quota_date DATE NOT NULL,
  service_item_id BIGINT,
  package_id BIGINT,
  seq INT NOT NULL,
  appointment_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_item_date_seq ON service_daily_quota (service_item_id, quota_date, seq);
CREATE UNIQUE INDEX uk_package_date_seq ON service_daily_quota (package_id, quota_date, seq);

CREATE TABLE appointment_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  appointment_date DATE NOT NULL,
  slot_time TIME NOT NULL
);
-- 防超卖的关键约束：同一员工同一天同一个 30 分钟切片只能被一单占用
CREATE UNIQUE INDEX uk_staff_date_slot ON appointment_slot (staff_id, appointment_date, slot_time);
CREATE INDEX idx_slot_appointment ON appointment_slot (appointment_id);

CREATE TABLE review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  score TINYINT NOT NULL,
  content VARCHAR(500),
  images VARCHAR(1000),
  reply_content VARCHAR(500),
  reply_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE shop (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  logo VARCHAR(500),
  phone VARCHAR(20),
  address VARCHAR(200),
  business_status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
);

CREATE TABLE shop_business_hour (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL,
  is_rest TINYINT NOT NULL DEFAULT 0,
  open_time TIME,
  close_time TIME
);
CREATE UNIQUE INDEX uk_shop_day ON shop_business_hour (shop_id, day_of_week);
