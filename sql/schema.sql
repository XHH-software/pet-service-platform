-- 宠物服务预约平台 数据库初始化脚本
-- MySQL 8.0+  /  charset utf8mb4
CREATE DATABASE IF NOT EXISTS pet_service_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE pet_service_platform;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS appointment_slot;
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
  position TINYINT NOT NULL COMMENT '1店长 2美容师 3助理',
  level TINYINT NOT NULL DEFAULT 1 COMMENT '1初级 2中级 3高级',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
) COMMENT '员工';

CREATE TABLE staff_specialty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_id BIGINT NOT NULL,
  service_item_id BIGINT NOT NULL,
  UNIQUE KEY uk_staff_item (staff_id, service_item_id)
) COMMENT '员工擅长项目';

CREATE TABLE category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  type TINYINT NOT NULL COMMENT '1服务项目 2服务套餐',
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_type_status (type, status)
) COMMENT '分类';

CREATE TABLE service_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  image VARCHAR(500),
  category_id BIGINT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  duration INT NOT NULL COMMENT '分钟',
  pet_size_limit TINYINT NOT NULL DEFAULT 0 COMMENT '0不限 1小型 2中型 3大型',
  daily_limit INT NOT NULL DEFAULT 10,
  description VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_category (category_id),
  KEY idx_status (status)
) COMMENT '服务项目';

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
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_category (category_id),
  KEY idx_status (status)
) COMMENT '服务套餐';

CREATE TABLE package_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  package_id BIGINT NOT NULL,
  service_item_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  KEY idx_package (package_id)
) COMMENT '套餐明细';

CREATE TABLE schedule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_id BIGINT NOT NULL,
  schedule_date DATE NOT NULL,
  start_time TIME,
  end_time TIME,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1可约 2休息',
  booked_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  UNIQUE KEY uk_staff_date (staff_id, schedule_date)
) COMMENT '排班';

CREATE TABLE customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid VARCHAR(100) UNIQUE,
  nickname VARCHAR(50),
  avatar VARCHAR(500),
  phone VARCHAR(20),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
) COMMENT '客户';

CREATE TABLE pet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  avatar VARCHAR(500),
  species TINYINT NOT NULL COMMENT '1狗 2猫 3其他',
  breed VARCHAR(50),
  gender TINYINT COMMENT '1公 2母',
  weight DECIMAL(5,2),
  birthday DATE,
  neutered TINYINT DEFAULT 0,
  vaccine_expiry_date DATE,
  allergies VARCHAR(200),
  remark VARCHAR(500),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_customer (customer_id)
) COMMENT '宠物';

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
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_customer (customer_id)
) COMMENT '客户地址';

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
  pay_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未付 1已付 2已退',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1待支付 2待服务 3服务中 4已取消 5已完成 6已评价',
  source TINYINT NOT NULL DEFAULT 1 COMMENT '1客户下单 2商家代下单',
  cancel_reason VARCHAR(200),
  remark VARCHAR(500),
  pay_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_date_staff (appointment_date, staff_id),
  KEY idx_customer (customer_id),
  KEY idx_status (status),
  KEY idx_create_time (create_time)
) COMMENT '预约';

CREATE TABLE appointment_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  service_item_id BIGINT,
  package_id BIGINT,
  service_name VARCHAR(100) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  duration INT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  subtotal DECIMAL(10,2) NOT NULL,
  KEY idx_appointment (appointment_id)
) COMMENT '预约项目明细';

CREATE TABLE appointment_slot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  appointment_date DATE NOT NULL,
  slot_time TIME NOT NULL,
  UNIQUE KEY uk_staff_date_slot (staff_id, appointment_date, slot_time),
  KEY idx_appointment (appointment_id)
) COMMENT '预约占用时间片（30分钟粒度）';

CREATE TABLE review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  score TINYINT NOT NULL COMMENT '1-5分',
  content VARCHAR(500),
  images VARCHAR(1000) COMMENT 'JSON数组',
  reply_content VARCHAR(500),
  reply_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT,
  KEY idx_customer (customer_id),
  KEY idx_staff (staff_id)
) COMMENT '评价';

CREATE TABLE shop (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  logo VARCHAR(500),
  phone VARCHAR(20),
  address VARCHAR(200),
  business_status TINYINT NOT NULL DEFAULT 1 COMMENT '1营业中 0休息中',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by BIGINT,
  update_by BIGINT
) COMMENT '门店';

CREATE TABLE shop_business_hour (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shop_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL COMMENT '1周一 7周日',
  is_rest TINYINT NOT NULL DEFAULT 0,
  open_time TIME,
  close_time TIME,
  UNIQUE KEY uk_shop_day (shop_id, day_of_week)
) COMMENT '营业时间';

SET FOREIGN_KEY_CHECKS = 1;
