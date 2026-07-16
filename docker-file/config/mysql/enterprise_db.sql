/*
 * Enterprise sample database for DataAgent (from data/aaa.sql)
 * Database: enterprise_db
 * Used as business datasource for NL2SQL / analytics demos.
 */

CREATE DATABASE IF NOT EXISTS enterprise_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE enterprise_db;

-- =============================================
-- 企业综合管理系统数据库脚本（完整版）
-- 包含：客户订单与需求预测统计表、采购供应执行明细表、采购供应汇报表、
--       售后记录统计表、设备维修记录统计表、统计样本表、
--       物料供需平衡明细表、物料供需平衡汇总表
-- 创建日期：2025-12-24
-- =============================================


-- =============================================
-- 创建客户订单与需求预测统计表
-- =============================================
DROP VIEW IF EXISTS v_product_month_linkage;
DROP VIEW IF EXISTS v_material_procurement_linkage;

DROP TABLE IF EXISTS customer_order_forecast;
CREATE TABLE customer_order_forecast (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    customer_code VARCHAR(50) COMMENT '客户编码',
    customer_name VARCHAR(200) COMMENT '客户名称',
    demand_year_month VARCHAR(20) COMMENT '需求年.月',
    product_category VARCHAR(50) COMMENT '产品分类/机型',
    product_code VARCHAR(100) COMMENT '产品编码',
    product_name VARCHAR(200) COMMENT '产品名称',
    specification VARCHAR(200) COMMENT '规格型号',
    product_unit VARCHAR(50) COMMENT '产品单位',
    planned_quantity DECIMAL(18,4) COMMENT '计划数量',
    order_quantity DECIMAL(18,4) COMMENT '订单数量',
    customer_request_quantity DECIMAL(18,4) COMMENT '客户要货数量',
    actual_delivery_quantity DECIMAL(18,4) COMMENT '实际出库数量',
    available_inventory_quantity DECIMAL(18,4) COMMENT '可用库存数量',
    customer_consignment_quantity DECIMAL(18,4) COMMENT '客户代管仓数量',
    supplier_reserve_quantity DECIMAL(18,4) COMMENT '配套供应商备货数量',
    internal_forecast_quantity DECIMAL(18,4) COMMENT '内部预测数量',
    order_forecast_deviation DECIMAL(18,4) COMMENT '订单和预测偏差',
    order_request_deviation DECIMAL(18,4) COMMENT '订单与要货偏差',
    order_delivery_deviation DECIMAL(18,4) COMMENT '订单与出库偏差',
    rolling_3month_order_quantity DECIMAL(18,4) COMMENT '3个月滚动订货量',
    rolling_6month_order_quantity DECIMAL(18,4) COMMENT '6个月滚动订货量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户订单与需求预测统计表';

-- =============================================
-- 创建采购供应执行明细表
-- =============================================
DROP TABLE IF EXISTS procurement_supply_detail;
CREATE TABLE procurement_supply_detail (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    purchase_order_no VARCHAR(100) COMMENT '采购订单号',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    material_unit VARCHAR(50) COMMENT '物料单位',
    purchase_quantity DECIMAL(18,4) COMMENT '采购数量',
    supplier_delivery_quantity DECIMAL(18,4) COMMENT '供应商送货数量',
    receipt_quantity DECIMAL(18,4) COMMENT '收货数量',
    inspection_qualified_quantity DECIMAL(18,4) COMMENT '检验合格数量',
    inspection_unqualified_quantity DECIMAL(18,4) COMMENT '检验不合格数量',
    concession_quantity DECIMAL(18,4) COMMENT '让步特采数量',
    warehousing_quantity DECIMAL(18,4) COMMENT '入库数量',
    delayed_receipt_quantity DECIMAL(18,4) COMMENT '延期收货数量',
    supply_shortage_quantity DECIMAL(18,4) COMMENT '供货短缺数量',
    order_status VARCHAR(50) COMMENT '订单状态',
    supplier_level VARCHAR(10) COMMENT '供应商等级',
    delivery_status VARCHAR(20) COMMENT '达期状态',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购供应执行明细表';

-- =============================================
-- 创建采购供应汇报表
-- =============================================
DROP TABLE IF EXISTS procurement_supply_report;
CREATE TABLE procurement_supply_report (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    supplier_code VARCHAR(50) COMMENT '供应商编码',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    material_unit VARCHAR(50) COMMENT '物料单位',
    purchase_quantity DECIMAL(18,4) COMMENT '采购数量',
    supplier_delivery_quantity DECIMAL(18,4) COMMENT '供应商送货数量',
    receipt_quantity DECIMAL(18,4) COMMENT '收货数量',
    inspection_qualified_quantity DECIMAL(18,4) COMMENT '检验合格数量',
    inspection_unqualified_quantity DECIMAL(18,4) COMMENT '检验不合格数量',
    concession_quantity DECIMAL(18,4) COMMENT '让步特采数量',
    warehousing_quantity DECIMAL(18,4) COMMENT '入库数量',
    delayed_receipt_quantity DECIMAL(18,4) COMMENT '延期收货数量',
    supply_shortage_quantity DECIMAL(18,4) COMMENT '供货短缺数量',
    on_time_delivery_times INT COMMENT '达期次数',
    delayed_delivery_times INT COMMENT '不达期次数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购供应汇报表';

-- =============================================
-- 创建售后记录统计表
-- =============================================
DROP TABLE IF EXISTS after_sales_statistics;
CREATE TABLE after_sales_statistics (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    product_type VARCHAR(50) COMMENT '产品类型',
    year VARCHAR(20) COMMENT '年度',
    period VARCHAR(20) COMMENT '期间',
    complaint_times INT COMMENT '客诉次数',
    return_exchange_times INT COMMENT '退/换次数',
    consultation_times INT COMMENT '咨询次数',
    other_record_times INT COMMENT '其他记录次数',
    total_service_applications INT COMMENT '服务申请总次数',
    completed_times INT COMMENT '已完成次数',
    pending_times INT COMMENT '待完成次数',
    completion_rate VARCHAR(10) COMMENT '完成率%',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后记录统计表';

-- =============================================
-- 创建设备维修记录统计表
-- =============================================
DROP TABLE IF EXISTS equipment_maintenance_statistics;
CREATE TABLE equipment_maintenance_statistics (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    comparison_year VARCHAR(20) COMMENT '对比年度',
    equipment_type VARCHAR(100) COMMENT '设备类型',
    equipment_quantity INT COMMENT '设备数量',
    normal_operation_hours DECIMAL(10,2) COMMENT '正常运行工时',
    fault_hours DECIMAL(10,2) COMMENT '故障工时',
    fault_times DECIMAL(10,2) COMMENT '故障次数',
    total_hours DECIMAL(10,2) COMMENT '总工时',
    invalid_hours DECIMAL(10,2) COMMENT '无效工时',
    mttr DECIMAL(10,2) COMMENT 'MTTR/平均修复时间',
    mttf DECIMAL(10,2) COMMENT 'MTTF/平均故障时间',
    mtbf DECIMAL(10,2) COMMENT 'MTBF/平均故障间隔时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备维修记录统计表';

-- =============================================
-- 创建统计样本表
-- =============================================
DROP TABLE IF EXISTS statistical_sample;
CREATE TABLE statistical_sample (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    sample_group VARCHAR(50) COMMENT '样本组别',
    X1 DECIMAL(10,4) COMMENT 'X1',
    X2 DECIMAL(10,4) COMMENT 'X2',
    X3 DECIMAL(10,4) COMMENT 'X3',
    X4 DECIMAL(10,4) COMMENT 'X4',
    X5 DECIMAL(10,4) COMMENT 'X5',
    X6 DECIMAL(10,4) COMMENT 'X6',
    X7 DECIMAL(10,4) COMMENT 'X7',
    X8 DECIMAL(10,4) COMMENT 'X8',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计样本表';

-- =============================================
-- 创建物料供需平衡明细表
-- =============================================
DROP TABLE IF EXISTS material_supply_detail;
CREATE TABLE material_supply_detail (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    demand_order_no VARCHAR(100) COMMENT '需求单号',
    product_code VARCHAR(100) COMMENT '产品编码',
    product_name VARCHAR(200) COMMENT '产品名称',
    bom_no VARCHAR(100) COMMENT '用料清单编号',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    material_unit VARCHAR(50) COMMENT '物料单位',
    demand_date DATE COMMENT '需求日期',
    demand_quantity DECIMAL(18,4) COMMENT '需求数量',
    available_quantity DECIMAL(18,4) COMMENT '可用数量',
    shortage_quantity DECIMAL(18,4) COMMENT '缺料数量',
    forecast_supply DECIMAL(18,4) COMMENT '展望期供应量',
    forecast_days INT COMMENT '展望天数',
    inventory_quantity DECIMAL(18,4) COMMENT '库存数量',
    purchase_in_transit_quantity DECIMAL(18,4) COMMENT '采购在途数量',
    production_in_process_quantity DECIMAL(18,4) COMMENT '生产在制数量',
    outsourcing_quantity DECIMAL(18,4) COMMENT '委外数量',
    supplier_reserve_quantity DECIMAL(18,4) COMMENT '供应商备货数量',
    other_supply_quantity DECIMAL(18,4) COMMENT '其他供给数量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料供需平衡明细表';

-- =============================================
-- 创建物料供需平衡汇总表
-- =============================================
DROP TABLE IF EXISTS material_supply_summary;
CREATE TABLE material_supply_summary (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'No.',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    material_unit VARCHAR(50) COMMENT '物料单位',
    demand_date DATE COMMENT '需求日期',
    demand_quantity DECIMAL(18,4) COMMENT '需求数量',
    available_quantity DECIMAL(18,4) COMMENT '可用数量',
    shortage_quantity DECIMAL(18,4) COMMENT '缺料数量',
    forecast_supply DECIMAL(18,4) COMMENT '展望期供应量',
    inventory_quantity DECIMAL(18,4) COMMENT '库存数量',
    purchase_in_transit_quantity DECIMAL(18,4) COMMENT '采购在途数量',
    production_in_process_quantity DECIMAL(18,4) COMMENT '生产在制数量',
    outsourcing_quantity DECIMAL(18,4) COMMENT '委外数量',
    supplier_reserve_quantity DECIMAL(18,4) COMMENT '供应商备货数量',
    other_supply_quantity DECIMAL(18,4) COMMENT '其他供给数量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料供需平衡汇总表';

-- =============================================
-- 插入数据 - 客户订单与需求预测统计表
-- =============================================

INSERT INTO customer_order_forecast 
(id, customer_code, customer_name, demand_year_month, product_category, product_code, product_name, specification, product_unit, planned_quantity, order_quantity, customer_request_quantity, actual_delivery_quantity, available_inventory_quantity, customer_consignment_quantity, supplier_reserve_quantity, internal_forecast_quantity, order_forecast_deviation, order_request_deviation, order_delivery_deviation, rolling_3month_order_quantity, rolling_6month_order_quantity)
VALUES
(1, 'VEN00001', '客户1', '2024.01', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 17158, 10239, 10239, 9931, 941, 67, NULL, 13600, -3361, 0, 308, NULL, NULL),
(2, 'VEN00001', '客户1', '2024.02', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 44060, 26547, 26547, 26016, 1924, 201, NULL, 37500, -10953, 0, 531, NULL, NULL),
(3, 'VEN00001', '客户1', '2024.03', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 51928, 28197, 28197, 27351, 2422, 215, NULL, 46400, -18203, 0, 846, 21661, NULL),
(4, 'VEN00001', '客户1', '2024.04', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 50955, 71513, 66607, 63276, 6572, 557, NULL, 41700, 29813, 4906, 8237, 42085, NULL),
(5, 'VEN00001', '客户1', '2024.05', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 113097, 119640, 55016, 52265, 10229, 929, NULL, 90300, 29340, 64624, 67375, 73116, NULL),
(6, 'VEN00001', '客户1', '2024.06', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 113523, 74649, 119814, 70917, 6793, 488, NULL, 90900, -16251, -45165, 3732, 88600, 55130),
(7, 'VEN00001', '客户1', '2024.07', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 121646, 68056, 68056, 66014, 6519, 500, NULL, 108200, -40144, 0, 2042, 87448, 64767),
(8, 'VEN00001', '客户1', '2024.08', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 220100, 95270, 95270, 91459, 8736, 749, NULL, 183000, -87730, 0, 3811, 79325, 76220),
(9, 'VEN00001', '客户1', '2024.09', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 157028, 156528, 103919, 101840, 13555, 907, NULL, 144500, 12028, 52609, 54688, 106618, 97609),
(10, 'VEN00001', '客户1', '2024.10', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 146662, 149450, 130485, 126570, 10506, 802, NULL, 114600, 34850, 18965, 22880, 133749, 110598),
(11, 'VEN00001', '客户1', '2024.11', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 149864, 183526, 135092, 128337, 16242, 1400, 5000, 130900, 52626, 48434, 55189, 163168, 121246),
(12, 'VEN00001', '客户1', '2024.12', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 195492, 151149, 151149, 148126, 14117, 999, NULL, 170300, -19151, 0, 3023, 161375, 133996),
(13, 'VEN00001', '客户1', '2025.01', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 348601, 279442, 214760, 206169, 26099, 1612, NULL, 316600, -37158, 64682, 73273, 204705, 169227),
(14, 'VEN00001', '客户1', '2025.02', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 171594, 254898, 240732, 240732, 19244, 1990, NULL, 145500, 109398, 14166, 14166, 228496, 195832),
(15, 'VEN00001', '客户1', '2025.03', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 204189, 133650, 133650, 129640, 10945, 800, NULL, 183900, -50250, 0, 4010, 222663, 192019),
(16, 'VEN00001', '客户1', '2025.04', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 327216, 166144, 166144, 157836, 12427, 956, NULL, 286900, -120756, 0, 8308, 184897, 194801),
(17, 'VEN00001', '客户1', '2025.05', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 378069, 241073, 180560, 171532, 20515, 1805, NULL, 327100, -86027, 60513, 69541, 180289, 204392),
(18, 'VEN00001', '客户1', '2025.06', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 219667, 246402, 182286, 173171, 18430, 1754, NULL, 192900, 53502, 64116, 73231, 217873, 220268),
(19, 'VEN00001', '客户1', '2025.07', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 273189, 218091, 218091, 213729, 15462, 1583, NULL, 242700, -24609, 0, 4362, 235188, 210043),
(20, 'VEN00001', '客户1', '2025.08', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 284730, 405620, 237700, 235323, 36181, 2295, NULL, 219200, 186420, 167920, 170297, 290037, 235163),
(21, 'VEN00001', '客户1', '2025.09', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 467000, 246130, 246130, 238746, 18016, 1565, NULL, 413100, -166970, 0, 7384, 289947, 253910),
(22, 'VEN00001', '客户1', '2025.10', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 505692, 403216, 224162, 224162, 31934, 2596, NULL, 417700, -14484, 179054, 179054, 351655, 293422),
(23, 'VEN00001', '客户1', '2025.11', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 314438, 300076, 341773, 288073, 29377, 1533, NULL, 252200, 47876, -41697, 12003, 316474, 303255),
(24, 'VEN00001', '客户1', '2025.12', 'A', 'Y01.0130', '智能控制器', 'GT-1', 'pcs', 424368, 494352, 381564, 362485, 40635, 2723, NULL, 375400, 118952, 112788, 131867, 399214, 344580),
(25, 'VEN00002', '客户2', '2024.01', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 257800, 224680, 224680, 222433, 15839, 1637, NULL, 207400, 17280, 0, 2247, NULL, NULL),
(26, 'VEN00002', '客户2', '2024.02', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 226366, 207213, 207213, 200996, 16991, 1079, NULL, 177400, 29813, 0, 6217, NULL, NULL),
(27, 'VEN00002', '客户2', '2024.03', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 317894, 209862, 209862, 207763, 18530, 1296, NULL, 292700, -82838, 0, 2099, 213918, NULL),
(28, 'VEN00002', '客户2', '2024.04', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 272660, 340334, 299846, 284853, 31651, 2259, NULL, 224600, 115734, 40488, 55481, 252469, NULL),
(29, 'VEN00002', '客户2', '2024.05', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 434806, 313374, 241515, 231854, 24161, 1642, NULL, 344800, -31426, 71859, 81520, 287856, NULL),
(30, 'VEN00002', '客户2', '2024.06', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 374718, 424688, 337760, 331004, 29898, 2340, 100000, 323900, 100788, 86928, 93684, 359465, 286691),
(31, 'VEN00002', '客户2', '2024.07', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 420074, 204889, 204889, 200791, 16165, 1262, NULL, 367400, -162511, 0, 4098, 314317, 283393),
(32, 'VEN00002', '客户2', '2024.08', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 315750, 210167, 210167, 201760, 14753, 1122, NULL, 247200, -37033, 0, 8407, 279914, 283885),
(33, 'VEN00002', '客户2', '2024.09', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 360329, 313627, 313627, 297945, 28853, 1590, NULL, 281800, 31827, 0, 15682, 242894, 301179),
(34, 'VEN00002', '客户2', '2024.10', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 474014, 240312, 240312, 240312, 20138, 1862, NULL, 395600, -155288, 0, 0, 254702, 284509),
(35, 'VEN00002', '客户2', '2024.11', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 291354, 418618, 241959, 234700, 29889, 2553, NULL, 257700, 160918, 176659, 183918, 324185, 302050),
(36, 'VEN00002', '客户2', '2024.12', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 304886, 339192, 339192, 325624, 33919, 2133, 15000, 276900, 62292, 0, 13568, 332707, 287800),
(37, 'VEN00002', '客户2', '2025.01', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 318101, 385628, 272406, 264233, 35015, 2313, NULL, 287200, 98428, 113222, 121395, 381146, 317924),
(38, 'VEN00002', '客户2', '2025.02', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 460154, 333209, 272434, 264260, 26123, 1739, NULL, 397100, -63891, 60775, 68949, 352676, 338431),
(39, 'VEN00002', '客户2', '2025.03', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 550784, 529172, 417778, 409422, 49371, 4069, NULL, 483800, 45372, 111394, 119750, 416003, 374355),
(40, 'VEN00002', '客户2', '2025.04', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 292608, 408650, 473984, 388218, 40660, 3269, NULL, 238800, 169850, -65334, 20432, 423677, 402411),
(41, 'VEN00002', '客户2', '2025.05', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 372170, 347122, 359553, 329766, 27978, 2658, NULL, 302600, 44522, -12431, 17356, 428314, 390495),
(42, 'VEN00002', '客户2', '2025.06', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 454916, 610960, 337500, 324000, 53825, 3842, NULL, 398200, 212760, 273460, 286960, 455577, 435790),
(43, 'VEN00002', '客户2', '2025.07', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 527343, 432958, 579961, 411310, 42213, 2454, NULL, 452700, -19742, -147003, 21648, 463680, 443678),
(44, 'VEN00002', '客户2', '2025.08', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 322009, 344502, 344502, 337611, 32555, 2222, 100000, 254600, 89902, 0, 6891, 462806, 445560),
(45, 'VEN00002', '客户2', '2025.09', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 497475, 408465, 408465, 388041, 32799, 2957, NULL, 409400, -935, 0, 20424, 395308, 425442),
(46, 'VEN00002', '客户2', '2025.10', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 476504, 386998, 386998, 383128, 31733, 2260, NULL, 393600, -6602, 0, 3870, 379988, 421834),
(47, 'VEN00002', '客户2', '2025.11', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 431560, 512817, 517125, 501611, 46615, 3989, NULL, 338600, 174217, -4308, 11206, 436093, 449450),
(48, 'VEN00002', '客户2', '2025.12', 'B', 'Y01.0131', '工业传感器', 'GT-2', 'pcs', 447574, 437376, 521785, 415507, 34508, 3083, NULL, 401400, 35976, -84409, 21869, 445730, 420519);

-- =============================================
-- 插入数据 - 采购供应执行明细表
-- =============================================

INSERT INTO procurement_supply_detail 
(id, supplier_code, supplier_name, purchase_order_no, material_code, material_name, specification, material_unit, purchase_quantity, supplier_delivery_quantity, receipt_quantity, inspection_qualified_quantity, inspection_unqualified_quantity, concession_quantity, warehousing_quantity, delayed_receipt_quantity, supply_shortage_quantity, order_status, supplier_level, delivery_status)
VALUES
(1, 'VEN00001', '深研电子', 'CGDD000076', 'ELEC.000001', '贴片电阻', 'R0603 10kΩ ±1% 02', 'pcs', 1000, 1000, 1000, 1000, NULL, NULL, 1000, NULL, NULL, '关闭', 'A', '正常'),
(2, 'VEN00001', '深研电子', 'CGDD000076', 'ELEC.000002', 'MLCC贴片电容', 'C0603 100nF X7R 50V 03', 'pcs', 2000, 2000, 2000, 2000, NULL, NULL, 2000, NULL, NULL, '关闭', 'A', '正常'),
(3, 'VEN00001', '深研电子', 'CGDD000076', 'ELEC.000006', '霍尔电流传感器', '0-500A 5V闭环 07', 'pcs', 3000, 3000, 3000, 3000, NULL, NULL, 3000, NULL, NULL, '关闭', 'A', '正常'),
(4, 'VEN00001', '深研电子', 'CGDD000076', 'CONN.000009', '高压互锁连接器', '800V 2芯 HVIL 橙色 10', 'pcs', 2500, 2500, 2000, 1800, NULL, 200, 2000, 500, NULL, '执行中', 'A', '异常'),
(5, 'VEN00001', '深研电子', 'CGDD000076', 'THERM.000023', '导热硅脂', '3.0W/mK 灰色 1kg/罐 24', 'kg', 1500, 1000, 1000, 950, 50, NULL, 1000, NULL, NULL, '关闭', 'A', '正常'),
(6, 'VEN00006', '宇通科技', 'CGDD000083', 'CABLE.000013', '高压屏蔽线缆', 'EVRP 35mm2 橙色屏蔽 14', 'm', 100, 100, 100, 100, NULL, NULL, 100, NULL, NULL, '关闭', 'B', '正常'),
(7, 'VEN00006', '宇通科技', 'CGDD000083', 'MOTOR.000030', '伺服电机', '750W 3000rpm 绝对值编码器 31', 'pcs', 50, 45, 45, 45, NULL, NULL, 45, NULL, 5, '关闭', 'B', '正常'),
(8, 'VEN00006', '宇通科技', 'CGDD000083', 'CABLE.000014', 'BMS采样线束', '24AWG 16芯 600mm 15', 'pcs', 350, 300, 300, 295, 5, NULL, 300, 50, NULL, '执行中', 'B', '异常');

-- =============================================
-- 插入数据 - 采购供应汇报表
-- =============================================

INSERT INTO procurement_supply_report 
(id, supplier_code, supplier_name, material_code, material_name, specification, material_unit, purchase_quantity, supplier_delivery_quantity, receipt_quantity, inspection_qualified_quantity, inspection_unqualified_quantity, concession_quantity, warehousing_quantity, delayed_receipt_quantity, supply_shortage_quantity, on_time_delivery_times, delayed_delivery_times)
VALUES
(1, 'VEN00001', '深研电子', 'ELEC.000001', '贴片电阻', 'R0603 10kΩ ±1% 02', 'pcs', 10000, 9500, 9000, 8750, 50, 200, 9000, 500, 0, 15, 3),
(2, 'VEN00006', '宇通科技', 'CABLE.000013', '高压屏蔽线缆', 'EVRP 35mm2 橙色屏蔽 14', 'm', 500, 445, 445, 440, 5, 0, 445, 50, 5, 8, 0);

-- =============================================
-- 插入数据 - 售后记录统计表
-- =============================================

INSERT INTO after_sales_statistics 
(id, product_type, year, period, complaint_times, return_exchange_times, consultation_times, other_record_times, total_service_applications, completed_times, pending_times, completion_rate)
VALUES
(1, 'A机型', '2024年', '1月', 9, 6, 67, 7, 89, 89, 0, '100%'),
(2, 'A机型', '2024年', '2月', 8, 5, 188, 11, 212, 212, 0, '100%'),
(3, 'A机型', '2024年', '3月', 5, 2, 191, 7, 205, 205, 0, '100%'),
(4, 'A机型', '2024年', '4月', 1, 0, 80, 9, 90, 90, 0, '100%'),
(5, 'A机型', '2024年', '5月', 4, 0, 79, 12, 95, 95, 0, '100%'),
(6, 'A机型', '2024年', '6月', 2, 5, 126, 10, 143, 143, 0, '100%'),
(7, 'A机型', '2024年', '7月', 1, 0, 60, 9, 70, 70, 0, '100%'),
(8, 'A机型', '2024年', '8月', 4, 5, 150, 9, 168, 168, 0, '100%'),
(9, 'A机型', '2024年', '9月', 6, 4, 176, 7, 193, 193, 0, '100%'),
(10, 'A机型', '2024年', '10月', 7, 4, 197, 6, 214, 214, 0, '100%'),
(11, 'A机型', '2024年', '11月', 8, 6, 197, 12, 223, 223, 0, '100%'),
(12, 'A机型', '2024年', '12月', 8, 5, 74, 5, 92, 92, 0, '100%'),
(13, 'A机型', '2025年', '1月', 5, 0, 52, 11, 68, 68, 0, '100%'),
(14, 'A机型', '2025年', '2月', 5, 2, 37, 12, 56, 56, 0, '100%'),
(15, 'A机型', '2025年', '3月', 2, 0, 70, 10, 82, 82, 0, '100%'),
(16, 'A机型', '2025年', '4月', 1, 3, 24, 9, 37, 37, 0, '100%'),
(17, 'A机型', '2025年', '5月', 8, 2, 137, 9, 156, 156, 0, '100%'),
(18, 'A机型', '2025年', '6月', 0, 5, 59, 11, 75, 75, 0, '100%'),
(19, 'A机型', '2025年', '7月', 7, 3, 116, 9, 135, 134, 1, '99%'),
(20, 'A机型', '2025年', '8月', 6, 4, 167, 9, 186, 185, 1, '99%'),
(21, 'A机型', '2025年', '9月', 2, 2, 70, 8, 82, 79, 3, '96%'),
(22, 'A机型', '2025年', '10月', 8, 0, 137, 6, 151, 150, 1, '99%'),
(23, 'A机型', '2025年', '11月', 7, 5, 39, 10, 61, 51, 10, '84%'),
(24, 'A机型', '2025年', '12月', 3, 4, 155, 9, 171, 167, 4, '98%');

-- =============================================
-- 插入数据 - 设备维修记录统计表
-- =============================================

INSERT INTO equipment_maintenance_statistics 
(id, comparison_year, equipment_type, equipment_quantity, normal_operation_hours, fault_hours, fault_times, total_hours, invalid_hours, mttr, mttf, mtbf)
VALUES
(1, '2024年', '数车加工中心', 11, 618, 3.6, 3.5, 621.6, 152, 1, 176.6, 177.6),
(2, '2024年', '注塑成型机', 11, 647.2, 5.9, 4.1, 653.1, 123.4, 1.4, 157.9, 159.3),
(3, '2024年', '全自动点胶机', 28, 652.4, 4.3, 4.5, 656.7, 121.5, 0.96, 144.98, 145.93),
(4, '2025年', '数车加工中心', 11, 625.8, 5, 4, 630.8, 122.9, 1.25, 156.5, 157.7),
(5, '2025年', '注塑成型机', 11, 673.3, 4.5, 4.4, 677.8, 104.8, 1, 153, 154),
(6, '2025年', '全自动点胶机', 28, 606.9, 4.6, 4.1, 611.5, 152.9, 1.1, 148, 149.1);

-- =============================================
-- 插入数据 - 统计样本表
-- =============================================

INSERT INTO statistical_sample (id, sample_group, X1, X2, X3, X4, X5, X6, X7, X8) VALUES
(1, '样本组1', 54.4103, 47.3238, 50.1780, 43.5794, 55.4543, 42.0898, 52.2541, 51.2112),
(2, '样本组2', 53.7663, 47.6287, 50.2069, 44.4209, 54.6026, 43.1999, 51.9172, 50.9805),
(3, '样本组3', 53.1223, 47.9336, 50.2358, 45.2624, 53.7508, 44.3101, 51.5803, 50.7497),
(4, '样本组4', 52.4783, 48.2385, 50.2648, 46.1039, 52.8991, 45.4202, 51.2434, 50.5190),
(5, '样本组5', 51.8343, 48.5435, 50.2937, 46.9454, 52.0473, 46.5303, 50.9065, 50.2883),
(6, '样本组6', 50.8455, 48.9608, 50.3906, 48.1445, 51.1597, 47.6958, 51.1170, 49.6148),
(7, '样本组7', 51.3248, 48.6062, 50.3935, 47.3599, 50.5486, 48.8928, 50.4210, 50.6331),
(8, '样本组8', 49.3796, 50.2150, 50.0927, 50.9338, 49.1899, 49.4100, 47.8765, 49.3115),
(9, '样本组9', 49.3472, 49.4409, 50.5873, 49.7582, 48.7734, 51.2238, 50.8421, 49.2862),
(10, '样本组10', 49.3489, 50.5090, 50.4297, 49.3971, 50.0853, 50.4409, 49.2663, 50.3318),
(11, '样本组11', 49.5084, 51.7754, 50.8950, 48.9249, 49.6786, 50.6660, 48.9662, 48.5342),
(12, '样本组12', 50.2408, 48.7232, 49.9690, 50.5822, 50.0915, 51.6448, 50.1997, 49.2294),
(13, '样本组13', 48.5149, 48.9897, 49.7895, 48.8365, 49.8612, 49.9089, 50.7043, 51.1313),
(14, '样本组14', 49.1222, 49.1261, 49.6892, 51.5926, 50.5342, 50.8171, 49.3319, 50.1860),
(15, '样本组15', 48.9861, 48.8886, 51.8732, 50.1397, 51.7825, 52.1716, 51.1783, 48.9898),
(16, '样本组16', 51.3281, 50.5731, 48.7684, 49.9826, 50.3144, 52.4527, 50.1399, 51.1518),
(17, '样本组17', 49.4647, 50.2167, 49.8260, 51.0623, 49.1536, 49.6176, 50.5458, 49.1724),
(18, '样本组18', 51.3983, 48.5371, 49.4657, 49.3942, 50.4724, 49.2722, 50.3705, 50.2725),
(19, '样本组19', 47.6476, 51.9908, 50.1065, 51.3798, 49.8974, 51.3133, 49.7105, 49.1097),
(20, '样本组20', 49.7422, 50.6692, 50.7213, 50.2079, 49.7147, 51.0819, 50.8874, 50.5376);

-- =============================================
-- 插入数据 - 物料供需平衡明细表
-- =============================================

INSERT INTO material_supply_detail 
(id, demand_order_no, product_code, product_name, bom_no, material_code, material_name, specification, material_unit, demand_date, demand_quantity, available_quantity, shortage_quantity, forecast_supply, forecast_days, inventory_quantity, purchase_in_transit_quantity, production_in_process_quantity, outsourcing_quantity, supplier_reserve_quantity, other_supply_quantity)
VALUES
(1, 'DD2025120001', 'Y01.0130', '智能控制器', 'BOM0001-01', 'ELEC.000001', '贴片电阻', 'R0603 10kΩ ±1% 02', 'pcs', '2025-12-24', 5000, 51000, 0, NULL, NULL, 8000, 20000, 0, 0, 20000, 3000),
(2, 'DD2025120002', 'Y01.0130', '智能控制器', 'BOM0001-01', 'ELEC.000002', 'MLCC贴片电容', 'C0603 100nF X7R 50V 03', 'pcs', '2025-12-24', 10000, 0, 10000, 500, 5, 0, 500, 0, 0, 0, 0),
(3, 'DD2025120003', 'Y01.0131', '工业传感器', 'BOM0002-03', 'ELEC.000006', '霍尔电流传感器', '0-500A 5V闭环 07', 'pcs', '2025-12-24', 15000, 0, 15000, 0, 5, 0, 0, 0, 0, 0, 0),
(4, 'DD2025120004', 'Y02.0201', '动力电池模组', 'BOM0003-02', 'CONN.000009', '高压互锁连接器', '800V 2芯 HVIL 橙色 10', 'pcs', '2025-12-24', 20000, 0, 20000, 0, 5, 0, 0, 0, 0, 0, 0),
(5, 'DD2025120005', 'Y01.0130', '智能控制器', 'BOM0001-02', 'THERM.000023', '导热硅脂', '3.0W/mK 灰色 1kg/罐 24', 'kg', '2025-12-24', 100, 800, 0, NULL, NULL, 300, 0, 200, 300, 0, 0),
(6, 'DD2025120006', 'Y02.0202', '车载连接器', 'BOM0004-01', 'CABLE.000013', '高压屏蔽线缆', 'EVRP 35mm2 橙色屏蔽 14', 'm', '2025-12-24', 200, 0, 200, 0, 5, 0, 0, 0, 0, 0, 0),
(7, 'DD2025120007', 'Y03.0310', '伺服驱动器', 'BOM0005-02', 'MOTOR.000030', '伺服电机', '750W 3000rpm 绝对值编码器 31', 'pcs', '2025-12-24', 500, 0, 500, 0, 5, 0, 0, 0, 0, 0, 0),
(8, 'DD2025120008', 'Y03.0311', '高压线束', 'BOM0006-02', 'CABLE.000014', 'BMS采样线束', '24AWG 16芯 600mm 15', 'pcs', '2025-12-24', 200, 0, 200, 0, 5, 0, 0, 0, 0, 0, 0);

-- =============================================
-- 插入数据 - 物料供需平衡汇总表
-- =============================================

INSERT INTO material_supply_summary 
(id, material_code, material_name, specification, material_unit, demand_date, demand_quantity, available_quantity, shortage_quantity, forecast_supply, inventory_quantity, purchase_in_transit_quantity, production_in_process_quantity, outsourcing_quantity, supplier_reserve_quantity, other_supply_quantity)
VALUES
(1, 'ELEC.000001', '贴片电阻', 'R0603 10kΩ ±1% 02', 'pcs', '2025-12-24', 5000, 51000, 0, 0, 8000, 20000, 0, 0, 20000, 3000),
(2, 'ELEC.000002', 'MLCC贴片电容', 'C0603 100nF X7R 50V 03', 'pcs', '2025-12-24', 10000, 0, 10000, 500, 0, 500, 0, 0, 0, 0),
(3, 'ELEC.000006', '霍尔电流传感器', '0-500A 5V闭环 07', 'pcs', '2025-12-24', 15000, 0, 15000, 0, 0, 0, 0, 0, 0, 0),
(4, 'CONN.000009', '高压互锁连接器', '800V 2芯 HVIL 橙色 10', 'pcs', '2025-12-24', 20000, 0, 20000, 0, 0, 0, 0, 0, 0, 0),
(5, 'THERM.000023', '导热硅脂', '3.0W/mK 灰色 1kg/罐 24', 'kg', '2025-12-24', 100, 800, 0, 0, 300, 0, 200, 300, 0, 0),
(6, 'CABLE.000013', '高压屏蔽线缆', 'EVRP 35mm2 橙色屏蔽 14', 'm', '2025-12-24', 200, 0, 200, 0, 0, 0, 0, 0, 0, 0),
(7, 'MOTOR.000030', '伺服电机', '750W 3000rpm 绝对值编码器 31', 'pcs', '2025-12-24', 500, 0, 500, 0, 0, 0, 0, 0, 0, 0),
(8, 'CABLE.000014', 'BMS采样线束', '24AWG 16芯 600mm 15', 'pcs', '2025-12-24', 200, 0, 200, 0, 0, 0, 0, 0, 0, 0);

-- =============================================
-- 创建索引以提高查询性能
-- =============================================

-- 客户订单与需求预测统计表索引
CREATE INDEX idx_customer_order_customer ON customer_order_forecast(customer_code);
CREATE INDEX idx_customer_order_year_month ON customer_order_forecast(demand_year_month);
CREATE INDEX idx_customer_order_product ON customer_order_forecast(product_code);
CREATE INDEX idx_customer_order_category ON customer_order_forecast(product_category);
CREATE INDEX idx_customer_order_customer_date ON customer_order_forecast(customer_code, demand_year_month);
CREATE INDEX idx_customer_order_product_date ON customer_order_forecast(product_code, demand_year_month);

-- 采购供应执行明细表索引
CREATE INDEX idx_procurement_detail_supplier ON procurement_supply_detail(supplier_code);
CREATE INDEX idx_procurement_detail_order ON procurement_supply_detail(purchase_order_no);
CREATE INDEX idx_procurement_detail_material ON procurement_supply_detail(material_code);
CREATE INDEX idx_procurement_detail_status ON procurement_supply_detail(order_status);
CREATE INDEX idx_procurement_detail_delivery_status ON procurement_supply_detail(delivery_status);
CREATE INDEX idx_procurement_detail_supplier_order ON procurement_supply_detail(supplier_code, purchase_order_no);

-- 采购供应汇报表索引
CREATE INDEX idx_procurement_report_supplier ON procurement_supply_report(supplier_code);
CREATE INDEX idx_procurement_report_material ON procurement_supply_report(material_code);
CREATE INDEX idx_procurement_report_supplier_material ON procurement_supply_report(supplier_code, material_code);

-- 售后记录统计表索引
CREATE INDEX idx_after_sales_product_type ON after_sales_statistics(product_type);
CREATE INDEX idx_after_sales_year ON after_sales_statistics(year);
CREATE INDEX idx_after_sales_period ON after_sales_statistics(period);
CREATE INDEX idx_after_sales_year_period ON after_sales_statistics(year, period);
CREATE INDEX idx_after_sales_product_year ON after_sales_statistics(product_type, year);

-- 设备维修记录统计表索引
CREATE INDEX idx_equipment_year ON equipment_maintenance_statistics(comparison_year);
CREATE INDEX idx_equipment_type ON equipment_maintenance_statistics(equipment_type);
CREATE INDEX idx_equipment_year_type ON equipment_maintenance_statistics(comparison_year, equipment_type);

-- 统计样本表索引
CREATE INDEX idx_sample_group ON statistical_sample(sample_group);

-- 明细表索引
CREATE INDEX idx_detail_material_code ON material_supply_detail(material_code);
CREATE INDEX idx_detail_demand_date ON material_supply_detail(demand_date);
CREATE INDEX idx_detail_material_date ON material_supply_detail(material_code, demand_date);

-- 汇总表索引
CREATE INDEX idx_summary_material_code ON material_supply_summary(material_code);
CREATE INDEX idx_summary_demand_date ON material_supply_summary(demand_date);
CREATE INDEX idx_summary_material_date ON material_supply_summary(material_code, demand_date);

-- =============================================
-- 添加额外测试数据
-- =============================================
-- =============================================
-- 批量生成测试数据（每个表200条）
-- 使用存储过程批量插入
-- =============================================

DELIMITER $$

-- =============================================
-- 1. 客户订单与需求预测统计表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_customer_order_forecast$$
CREATE PROCEDURE generate_customer_order_forecast()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_customer_code VARCHAR(50);
    DECLARE v_customer_name VARCHAR(200);
    DECLARE v_year_month VARCHAR(20);
    DECLARE v_product_category VARCHAR(50);
    DECLARE v_product_code VARCHAR(100);
    DECLARE v_product_name VARCHAR(200);
    
    WHILE i < 200 DO
        SET v_customer_code = CONCAT('VEN', LPAD(FLOOR(1 + RAND() * 50), 5, '0'));
        SET v_customer_name = CONCAT('客户', FLOOR(1 + RAND() * 50));
        SET v_year_month = CONCAT(
            FLOOR(2023 + RAND() * 3), '.',
            LPAD(FLOOR(1 + RAND() * 12), 2, '0')
        );
        SET v_product_category = ELT(FLOOR(1 + RAND() * 5), 'A', 'B', 'C', 'D', 'E');
        SET v_product_code = CONCAT(
            ELT(FLOOR(1 + RAND() * 5), 'Y01', 'Y02', 'Y03', 'Y04', 'Y05'),
            '.', LPAD(FLOOR(1 + RAND() * 999), 4, '0')
        );
        SET v_product_name = CONCAT('产品', FLOOR(1 + RAND() * 20));
        
        INSERT INTO customer_order_forecast (
            customer_code, customer_name, demand_year_month, product_category,
            product_code, product_name, specification, product_unit,
            planned_quantity, order_quantity, customer_request_quantity,
            actual_delivery_quantity, available_inventory_quantity,
            customer_consignment_quantity, supplier_reserve_quantity,
            internal_forecast_quantity, order_forecast_deviation,
            order_request_deviation, order_delivery_deviation,
            rolling_3month_order_quantity, rolling_6month_order_quantity
        ) VALUES (
            v_customer_code, v_customer_name, v_year_month, v_product_category,
            v_product_code, v_product_name, CONCAT('GT-', FLOOR(1 + RAND() * 10)),
            ELT(FLOOR(1 + RAND() * 3), 'pcs', 'kg', 'm'),
            ROUND(1000 + RAND() * 500000, 4),
            ROUND(1000 + RAND() * 500000, 4),
            ROUND(1000 + RAND() * 500000, 4),
            ROUND(1000 + RAND() * 480000, 4),
            ROUND(100 + RAND() * 50000, 4),
            ROUND(10 + RAND() * 5000, 4),
            ROUND(100 + RAND() * 100000, 4),
            ROUND(1000 + RAND() * 400000, 4),
            ROUND(-100000 + RAND() * 200000, 4),
            ROUND(-50000 + RAND() * 100000, 4),
            ROUND(-50000 + RAND() * 100000, 4),
            ROUND(1000 + RAND() * 400000, 4),
            ROUND(1000 + RAND() * 350000, 4)
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 2. 采购供应执行明细表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_procurement_supply_detail$$
CREATE PROCEDURE generate_procurement_supply_detail()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_supplier_code VARCHAR(50);
    DECLARE v_supplier_name VARCHAR(200);
    DECLARE v_material_code VARCHAR(100);
    DECLARE v_material_name VARCHAR(200);
    
    WHILE i < 200 DO
        SET v_supplier_code = CONCAT('VEN', LPAD(FLOOR(1 + RAND() * 30), 5, '0'));
        SET v_supplier_name = CONCAT('供应商', FLOOR(1 + RAND() * 30));
        SET v_material_code = CONCAT(
            ELT(FLOOR(1 + RAND() * 5), 'Y01', 'Z10', 'M00', 'P20', 'C30'),
            '.', LPAD(FLOOR(1 + RAND() * 9999), 4, '0')
        );
        SET v_material_name = CONCAT('物料', FLOOR(1 + RAND() * 50));
        
        INSERT INTO procurement_supply_detail (
            supplier_code, supplier_name, purchase_order_no, material_code,
            material_name, specification, material_unit, purchase_quantity,
            supplier_delivery_quantity, receipt_quantity,
            inspection_qualified_quantity, inspection_unqualified_quantity,
            concession_quantity, warehousing_quantity, delayed_receipt_quantity,
            supply_shortage_quantity, order_status, supplier_level, delivery_status
        ) VALUES (
            v_supplier_code, v_supplier_name,
            CONCAT('CGDD', LPAD(FLOOR(1 + RAND() * 999999), 6, '0')),
            v_material_code, v_material_name,
            CONCAT('规格', FLOOR(1 + RAND() * 100)),
            ELT(FLOOR(1 + RAND() * 4), 'pcs', 'kg', 'm', 'L'),
            ROUND(10 + RAND() * 10000, 4),
            ROUND(10 + RAND() * 9500, 4),
            ROUND(10 + RAND() * 9200, 4),
            ROUND(10 + RAND() * 9000, 4),
            ROUND(0 + RAND() * 500, 4),
            ROUND(0 + RAND() * 200, 4),
            ROUND(10 + RAND() * 9000, 4),
            ROUND(0 + RAND() * 500, 4),
            ROUND(0 + RAND() * 300, 4),
            ELT(FLOOR(1 + RAND() * 3), '关闭', '执行中', '已下单'),
            ELT(FLOOR(1 + RAND() * 4), 'A', 'B', 'C', 'D'),
            ELT(FLOOR(1 + RAND() * 3), '正常', '异常', '待确认')
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 3. 采购供应汇报表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_procurement_supply_report$$
CREATE PROCEDURE generate_procurement_supply_report()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_supplier_code VARCHAR(50);
    DECLARE v_supplier_name VARCHAR(200);
    
    WHILE i < 200 DO
        SET v_supplier_code = CONCAT('VEN', LPAD(FLOOR(1 + RAND() * 30), 5, '0'));
        SET v_supplier_name = CONCAT('供应商', FLOOR(1 + RAND() * 30));
        
        INSERT INTO procurement_supply_report (
            supplier_code, supplier_name, material_code, material_name,
            specification, material_unit, purchase_quantity,
            supplier_delivery_quantity, receipt_quantity,
            inspection_qualified_quantity, inspection_unqualified_quantity,
            concession_quantity, warehousing_quantity, delayed_receipt_quantity,
            supply_shortage_quantity, on_time_delivery_times, delayed_delivery_times
        ) VALUES (
            v_supplier_code, v_supplier_name,
            CONCAT('M', LPAD(FLOOR(1 + RAND() * 9999), 6, '0')),
            CONCAT('物料', FLOOR(1 + RAND() * 50)),
            CONCAT('规格', FLOOR(1 + RAND() * 100)),
            ELT(FLOOR(1 + RAND() * 4), 'pcs', 'kg', 'm', 'L'),
            ROUND(100 + RAND() * 50000, 4),
            ROUND(100 + RAND() * 48000, 4),
            ROUND(100 + RAND() * 46000, 4),
            ROUND(100 + RAND() * 45000, 4),
            ROUND(0 + RAND() * 1000, 4),
            ROUND(0 + RAND() * 500, 4),
            ROUND(100 + RAND() * 45000, 4),
            ROUND(0 + RAND() * 2000, 4),
            ROUND(0 + RAND() * 1000, 4),
            FLOOR(1 + RAND() * 50),
            FLOOR(0 + RAND() * 10)
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 4. 售后记录统计表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_after_sales_statistics$$
CREATE PROCEDURE generate_after_sales_statistics()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_year VARCHAR(20);
    DECLARE v_period VARCHAR(20);
    DECLARE v_product_type VARCHAR(50);
    DECLARE v_total INT;
    DECLARE v_completed INT;
    
    WHILE i < 200 DO
        SET v_year = CONCAT(FLOOR(2020 + RAND() * 6), '年');
        SET v_period = CONCAT(FLOOR(1 + RAND() * 12), '月');
        SET v_product_type = ELT(FLOOR(1 + RAND() * 6), 'A机型', 'B机型', 'C机型', 'D机型', 'E机型', 'F机型');
        SET v_total = FLOOR(10 + RAND() * 300);
        SET v_completed = FLOOR(v_total * (0.85 + RAND() * 0.15));
        
        INSERT INTO after_sales_statistics (
            product_type, year, period, complaint_times, return_exchange_times,
            consultation_times, other_record_times, total_service_applications,
            completed_times, pending_times, completion_rate
        ) VALUES (
            v_product_type, v_year, v_period,
            FLOOR(0 + RAND() * 15),
            FLOOR(0 + RAND() * 10),
            FLOOR(10 + RAND() * 200),
            FLOOR(0 + RAND() * 20),
            v_total,
            v_completed,
            v_total - v_completed,
            CONCAT(ROUND(v_completed / v_total * 100, 0), '%')
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 5. 设备维修记录统计表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_equipment_maintenance$$
CREATE PROCEDURE generate_equipment_maintenance()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_year VARCHAR(20);
    DECLARE v_equipment_type VARCHAR(100);
    DECLARE v_quantity INT;
    DECLARE v_normal_hours DECIMAL(10,2);
    DECLARE v_fault_hours DECIMAL(10,2);
    
    WHILE i < 200 DO
        SET v_year = CONCAT(FLOOR(2018 + RAND() * 8), '年');
        SET v_equipment_type = ELT(FLOOR(1 + RAND() * 8),
            '数车加工中心', '注塑成型机', '全自动点胶机', 'CNC加工中心',
            '激光切割机', '冲压机', '焊接机器人', '自动化流水线'
        );
        SET v_quantity = FLOOR(5 + RAND() * 50);
        SET v_normal_hours = ROUND(500 + RAND() * 300, 2);
        SET v_fault_hours = ROUND(1 + RAND() * 20, 2);
        
        INSERT INTO equipment_maintenance_statistics (
            comparison_year, equipment_type, equipment_quantity,
            normal_operation_hours, fault_hours, fault_times,
            total_hours, invalid_hours, mttr, mttf, mtbf
        ) VALUES (
            v_year, v_equipment_type, v_quantity,
            v_normal_hours,
            v_fault_hours,
            ROUND(1 + RAND() * 10, 2),
            ROUND(v_normal_hours + v_fault_hours + 50 + RAND() * 100, 2),
            ROUND(50 + RAND() * 150, 2),
            ROUND(0.5 + RAND() * 5, 2),
            ROUND(100 + RAND() * 200, 2),
            ROUND(100 + RAND() * 200, 2)
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 6. 统计样本表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_statistical_sample$$
CREATE PROCEDURE generate_statistical_sample()
BEGIN
    DECLARE i INT DEFAULT 0;
    
    WHILE i < 200 DO
        INSERT INTO statistical_sample (
            sample_group, X1, X2, X3, X4, X5, X6, X7, X8
        ) VALUES (
            CONCAT('样本组', FLOOR(1 + RAND() * 50)),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4),
            ROUND(40 + RAND() * 20, 4)
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 7. 物料供需平衡明细表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_material_supply_detail$$
CREATE PROCEDURE generate_material_supply_detail()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_material_code VARCHAR(100);
    DECLARE v_material_name VARCHAR(200);
    DECLARE v_demand_qty DECIMAL(18,4);
    DECLARE v_avail_qty DECIMAL(18,4);
    
    WHILE i < 200 DO
        SET v_material_code = CONCAT(
            ELT(FLOOR(1 + RAND() * 5), '863', 'M00', 'P20', 'C30', 'Z10'),
            '.', LPAD(FLOOR(1 + RAND() * 99999), 6, '0')
        );
        SET v_material_name = CONCAT('物料', FLOOR(1 + RAND() * 100));
        SET v_demand_qty = ROUND(100 + RAND() * 50000, 4);
        SET v_avail_qty = ROUND(0 + RAND() * 60000, 4);
        
        INSERT INTO material_supply_detail (
            demand_order_no, product_code, product_name, bom_no,
            material_code, material_name, specification, material_unit,
            demand_date, demand_quantity, available_quantity,
            shortage_quantity, forecast_supply, forecast_days,
            inventory_quantity, purchase_in_transit_quantity,
            production_in_process_quantity, outsourcing_quantity,
            supplier_reserve_quantity, other_supply_quantity
        ) VALUES (
            CONCAT('DD', LPAD(FLOOR(1 + RAND() * 999999), 6, '0')),
            CONCAT('P', LPAD(FLOOR(1 + RAND() * 999), 4, '0')),
            CONCAT('产品', FLOOR(1 + RAND() * 50)),
            CONCAT('BOM', LPAD(FLOOR(1 + RAND() * 999), 4, '0')),
            v_material_code, v_material_name,
            CONCAT('规格', FLOOR(1 + RAND() * 100)),
            ELT(FLOOR(1 + RAND() * 4), 'kg', 'pcs', 'm', 'L'),
            DATE_ADD('2024-01-01', INTERVAL FLOOR(RAND() * 730) DAY),
            v_demand_qty,
            v_avail_qty,
            ROUND(v_demand_qty - v_avail_qty, 4),
            ROUND(0 + RAND() * 10000, 4),
            FLOOR(1 + RAND() * 30),
            ROUND(0 + RAND() * 20000, 4),
            ROUND(0 + RAND() * 15000, 4),
            ROUND(0 + RAND() * 10000, 4),
            ROUND(0 + RAND() * 8000, 4),
            ROUND(0 + RAND() * 10000, 4),
            ROUND(0 + RAND() * 5000, 4)
        );
        SET i = i + 1;
    END WHILE;
END$$

-- =============================================
-- 8. 物料供需平衡汇总表 - 生成200条数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_material_supply_summary$$
CREATE PROCEDURE generate_material_supply_summary()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_material_code VARCHAR(100);
    DECLARE v_material_name VARCHAR(200);
    DECLARE v_demand_qty DECIMAL(18,4);
    DECLARE v_avail_qty DECIMAL(18,4);
    
    WHILE i < 200 DO
        SET v_material_code = CONCAT(
            ELT(FLOOR(1 + RAND() * 5), '863', 'M00', 'P20', 'C30', 'Z10'),
            '.', LPAD(FLOOR(1 + RAND() * 99999), 6, '0')
        );
        SET v_material_name = CONCAT('物料', FLOOR(1 + RAND() * 100));
        SET v_demand_qty = ROUND(100 + RAND() * 100000, 4);
        SET v_avail_qty = ROUND(0 + RAND() * 120000, 4);
        
        INSERT INTO material_supply_summary (
            material_code, material_name, specification, material_unit,
            demand_date, demand_quantity, available_quantity,
            shortage_quantity, forecast_supply, inventory_quantity,
            purchase_in_transit_quantity, production_in_process_quantity,
            outsourcing_quantity, supplier_reserve_quantity, other_supply_quantity
        ) VALUES (
            v_material_code, v_material_name,
            CONCAT('规格', FLOOR(1 + RAND() * 100)),
            ELT(FLOOR(1 + RAND() * 4), 'kg', 'pcs', 'm', 'L'),
            DATE_ADD('2024-01-01', INTERVAL FLOOR(RAND() * 730) DAY),
            v_demand_qty,
            v_avail_qty,
            ROUND(v_demand_qty - v_avail_qty, 4),
            ROUND(0 + RAND() * 15000, 4),
            ROUND(0 + RAND() * 30000, 4),
            ROUND(0 + RAND() * 20000, 4),
            ROUND(0 + RAND() * 15000, 4),
            ROUND(0 + RAND() * 10000, 4),
            ROUND(0 + RAND() * 15000, 4),
            ROUND(0 + RAND() * 8000, 4)
        );
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

-- =============================================
-- 执行存储过程生成数据
-- =============================================
-- 原始200条随机样本生成器保留为模板，不再执行。
-- 下方“增强版真实业务样本数据”会生成更大规模、字段联动更合理的数据。
-- CALL generate_customer_order_forecast();
-- CALL generate_procurement_supply_detail();
-- CALL generate_procurement_supply_report();
-- CALL generate_after_sales_statistics();
-- CALL generate_equipment_maintenance();
-- CALL generate_statistical_sample();
-- CALL generate_material_supply_detail();
-- CALL generate_material_supply_summary();

-- =============================================
-- 删除存储过程（清理）
-- =============================================
DROP PROCEDURE IF EXISTS generate_customer_order_forecast;
DROP PROCEDURE IF EXISTS generate_procurement_supply_detail;
DROP PROCEDURE IF EXISTS generate_procurement_supply_report;
DROP PROCEDURE IF EXISTS generate_after_sales_statistics;
DROP PROCEDURE IF EXISTS generate_equipment_maintenance;
DROP PROCEDURE IF EXISTS generate_statistical_sample;
DROP PROCEDURE IF EXISTS generate_material_supply_detail;
DROP PROCEDURE IF EXISTS generate_material_supply_summary;

-- =============================================
-- 增强版真实业务样本数据
-- 说明：
-- 1. 客户订单按客户、产品、月份生成连续时序数据，并计算3/6个月滚动订货量。
-- 2. 采购执行明细按供应商等级推导交付、收货、质检、入库、延期、短缺。
-- 3. 采购汇报表从采购明细聚合生成，避免汇总口径与明细不一致。
-- 4. 售后、设备维修、统计样本、物料供需按业务口径扩大样本量。
-- 5. 物料供需汇总表从供需明细聚合生成，缺料数量按汇总需求与可用量重算。
-- =============================================

DELIMITER $$

-- =============================================
-- 1. 客户订单与需求预测统计表 - 生成约12000条连续月度数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_customer_order_forecast$$
CREATE PROCEDURE generate_real_customer_order_forecast()
BEGIN
    DECLARE v_customer_id INT DEFAULT 1;
    DECLARE v_product_id INT DEFAULT 1;
    DECLARE v_month_offset INT DEFAULT 0;
    DECLARE v_period_date DATE;
    DECLARE v_year_month VARCHAR(20);
    DECLARE v_month INT;
    DECLARE v_customer_code VARCHAR(50);
    DECLARE v_customer_name VARCHAR(200);
    DECLARE v_product_category VARCHAR(50);
    DECLARE v_product_code VARCHAR(100);
    DECLARE v_product_name VARCHAR(200);
    DECLARE v_specification VARCHAR(200);
    DECLARE v_product_unit VARCHAR(50);
    DECLARE v_base_demand DECIMAL(18,4);
    DECLARE v_season_factor DECIMAL(10,4);
    DECLARE v_growth_factor DECIMAL(10,4);
    DECLARE v_plan_qty DECIMAL(18,4);
    DECLARE v_forecast_qty DECIMAL(18,4);
    DECLARE v_order_qty DECIMAL(18,4);
    DECLARE v_request_qty DECIMAL(18,4);
    DECLARE v_delivery_qty DECIMAL(18,4);
    DECLARE v_inventory_qty DECIMAL(18,4);
    DECLARE v_consignment_qty DECIMAL(18,4);
    DECLARE v_reserve_qty DECIMAL(18,4);
    DECLARE v_prev1 DECIMAL(18,4);
    DECLARE v_prev2 DECIMAL(18,4);
    DECLARE v_prev3 DECIMAL(18,4);
    DECLARE v_prev4 DECIMAL(18,4);
    DECLARE v_prev5 DECIMAL(18,4);

    WHILE v_customer_id <= 25 DO
        SET v_customer_code = CONCAT('CUS', LPAD(v_customer_id, 5, '0'));
        SET v_customer_name = ELT(v_customer_id,
            '华东新能源股份有限公司', '深蓝汽车零部件有限公司', '长江电子科技有限公司',
            '北方智能装备集团', '南粤精密制造有限公司', '星河电气系统有限公司',
            '海川工业控制有限公司', '云岭储能科技有限公司', '东辰通信设备有限公司',
            '中科轨交装备有限公司', '远航医疗器械有限公司', '瑞景机器人有限公司',
            '天元光伏材料有限公司', '西部矿山装备有限公司', '鸿信家电制造有限公司',
            '启明半导体封测有限公司', '宏泰工程机械有限公司', '新港船舶配套有限公司',
            '金桥仪器仪表有限公司', '联创数据中心设备有限公司', '青峰环保科技有限公司',
            '万和电源系统有限公司', '盛达包装机械有限公司', '广源农业装备有限公司',
            '鼎新工业自动化有限公司'
        );

        SET v_product_id = 1;
        WHILE v_product_id <= 10 DO
            SET v_product_category = ELT(v_product_id, 'A', 'A', 'B', 'B', 'C', 'C', 'D', 'D', 'E', 'E');
            SET v_product_code = ELT(v_product_id,
                'Y01.0130', 'Y01.0131', 'Y02.0201', 'Y02.0202', 'Y03.0310',
                'Y03.0311', 'Y04.0408', 'Y04.0412', 'Y05.0506', 'Y05.0518'
            );
            SET v_product_name = ELT(v_product_id,
                '智能控制器', '工业传感器', '动力电池模组', '车载连接器', '伺服驱动器',
                '高压线束', '精密结构件', '通信网关', '液冷板组件', '电源管理模块'
            );
            SET v_specification = ELT(v_product_id,
                'GT-100 标准型', 'GT-120 防护型', 'GT-200 48V/100Ah', 'GT-210 高压互锁',
                'GT-300 1.5kW', 'GT-310 800V', 'GT-400 铝合金', 'GT-410 双网口',
                'GT-500 12通道', 'GT-510 300A'
            );
            SET v_product_unit = IF(v_product_id IN (3, 5, 8, 9), '套', 'pcs');
            SET v_prev1 = NULL;
            SET v_prev2 = NULL;
            SET v_prev3 = NULL;
            SET v_prev4 = NULL;
            SET v_prev5 = NULL;
            SET v_month_offset = 0;

            WHILE v_month_offset < 48 DO
                SET v_period_date = DATE_ADD('2023-01-01', INTERVAL v_month_offset MONTH);
                SET v_year_month = DATE_FORMAT(v_period_date, '%Y.%m');
                SET v_month = MONTH(v_period_date);
                SET v_base_demand = 1200 + v_customer_id * 180 + v_product_id * 320;
                SET v_season_factor = CASE
                    WHEN v_month IN (3, 6, 9, 11) THEN 1.18
                    WHEN v_month IN (1, 2) THEN 0.78
                    WHEN v_month IN (7, 8) THEN 0.92
                    ELSE 1.00
                END;
                SET v_growth_factor = 1 + (YEAR(v_period_date) - 2023) * 0.08 + (v_month - 1) * 0.002;
                SET v_plan_qty = ROUND(v_base_demand * v_season_factor * v_growth_factor * (0.92 + RAND() * 0.16), 0);
                SET v_forecast_qty = ROUND(v_plan_qty * (0.88 + RAND() * 0.20), 0);

                IF v_month IN (6, 11) THEN
                    SET v_forecast_qty = ROUND(v_forecast_qty * (1.05 + RAND() * 0.10), 0);
                END IF;

                SET v_order_qty = ROUND(v_forecast_qty * (0.82 + RAND() * 0.36), 0);

                IF RAND() < 0.06 THEN
                    SET v_order_qty = ROUND(v_order_qty * (1.25 + RAND() * 0.35), 0);
                END IF;

                IF RAND() < 0.04 THEN
                    SET v_order_qty = ROUND(v_order_qty * (0.55 + RAND() * 0.25), 0);
                END IF;

                SET v_request_qty = ROUND(v_order_qty * (0.88 + RAND() * 0.20), 0);
                SET v_delivery_qty = ROUND(LEAST(v_request_qty, v_order_qty * (0.90 + RAND() * 0.10)), 0);

                IF RAND() < 0.08 THEN
                    SET v_delivery_qty = ROUND(v_delivery_qty * (0.75 + RAND() * 0.20), 0);
                END IF;

                SET v_inventory_qty = ROUND(v_order_qty * (0.04 + RAND() * 0.16), 0);
                SET v_consignment_qty = ROUND(v_order_qty * (0.005 + RAND() * 0.035), 0);
                SET v_reserve_qty = IF(RAND() < 0.35, ROUND(v_order_qty * (0.03 + RAND() * 0.20), 0), 0);

                INSERT INTO customer_order_forecast (
                    customer_code, customer_name, demand_year_month, product_category,
                    product_code, product_name, specification, product_unit,
                    planned_quantity, order_quantity, customer_request_quantity,
                    actual_delivery_quantity, available_inventory_quantity,
                    customer_consignment_quantity, supplier_reserve_quantity,
                    internal_forecast_quantity, order_forecast_deviation,
                    order_request_deviation, order_delivery_deviation,
                    rolling_3month_order_quantity, rolling_6month_order_quantity
                ) VALUES (
                    v_customer_code, v_customer_name, v_year_month, v_product_category,
                    v_product_code, v_product_name, v_specification, v_product_unit,
                    v_plan_qty, v_order_qty, v_request_qty, v_delivery_qty,
                    v_inventory_qty, v_consignment_qty, v_reserve_qty,
                    v_forecast_qty, v_order_qty - v_forecast_qty,
                    v_order_qty - v_request_qty, v_order_qty - v_delivery_qty,
                    IF(v_month_offset >= 2, ROUND((v_order_qty + v_prev1 + v_prev2) / 3, 4), NULL),
                    IF(v_month_offset >= 5, ROUND((v_order_qty + v_prev1 + v_prev2 + v_prev3 + v_prev4 + v_prev5) / 6, 4), NULL)
                );

                SET v_prev5 = v_prev4;
                SET v_prev4 = v_prev3;
                SET v_prev3 = v_prev2;
                SET v_prev2 = v_prev1;
                SET v_prev1 = v_order_qty;
                SET v_month_offset = v_month_offset + 1;
            END WHILE;

            SET v_product_id = v_product_id + 1;
        END WHILE;

        SET v_customer_id = v_customer_id + 1;
    END WHILE;
END$$

-- =============================================
-- 2. 采购供应执行明细表 - 生成12000条采购履约明细
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_procurement_supply_detail$$
CREATE PROCEDURE generate_real_procurement_supply_detail()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_target_count INT DEFAULT 12000;
    DECLARE v_supplier_id INT;
    DECLARE v_material_id INT;
    DECLARE v_supplier_code VARCHAR(50);
    DECLARE v_supplier_name VARCHAR(200);
    DECLARE v_supplier_level VARCHAR(10);
    DECLARE v_material_code VARCHAR(100);
    DECLARE v_material_name VARCHAR(200);
    DECLARE v_specification VARCHAR(200);
    DECLARE v_unit VARCHAR(50);
    DECLARE v_order_status VARCHAR(50);
    DECLARE v_delivery_status VARCHAR(20);
    DECLARE v_status_rand DECIMAL(10,4);
    DECLARE v_purchase_qty DECIMAL(18,4);
    DECLARE v_delivery_rate DECIMAL(10,4);
    DECLARE v_delivery_qty DECIMAL(18,4);
    DECLARE v_receipt_qty DECIMAL(18,4);
    DECLARE v_quality_rate DECIMAL(10,4);
    DECLARE v_unqualified_qty DECIMAL(18,4);
    DECLARE v_qualified_qty DECIMAL(18,4);
    DECLARE v_concession_qty DECIMAL(18,4);
    DECLARE v_warehousing_qty DECIMAL(18,4);
    DECLARE v_delayed_qty DECIMAL(18,4);
    DECLARE v_shortage_qty DECIMAL(18,4);
    DECLARE v_late_probability DECIMAL(10,4);
    DECLARE v_po_month DATE;

    WHILE v_i < v_target_count DO
        SET v_supplier_id = FLOOR(1 + RAND() * 40);
        SET v_material_id = MOD(v_i, 192) + 1;
        SET v_supplier_code = CONCAT('SUP', LPAD(v_supplier_id, 5, '0'));
        SET v_supplier_name = CONCAT(ELT(MOD(v_supplier_id - 1, 20) + 1,
            '华南电子', '远东连接器', '星辉金属', '海纳塑胶', '联创线缆',
            '精工模具', '鼎盛化工', '瑞达包装', '宏信传感', '天启电源',
            '安捷物流', '三合五金', '博远光电', '金源材料', '科瑞自动化',
            '德胜涂装', '盛泰电机', '启航铝业', '新锐硅材', '广通机电'
        ), IF(v_supplier_id > 20, '二厂', ''));
        SET v_supplier_level = CASE
            WHEN v_supplier_id <= 8 THEN 'A'
            WHEN v_supplier_id <= 24 THEN 'B'
            WHEN v_supplier_id <= 34 THEN 'C'
            ELSE 'D'
        END;
        SET v_material_code = CONCAT(
            ELT(MOD(v_material_id - 1, 32) + 1,
                'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC',
                'CONN', 'CONN', 'CONN', 'CONN', 'CABLE', 'CABLE', 'CABLE', 'CABLE',
                'MECH', 'MECH', 'MECH', 'MECH', 'MECH', 'MECH', 'THERM', 'THERM',
                'CHEM', 'CHEM', 'CHEM', 'PACK', 'PACK', 'MOTOR', 'MOTOR', 'TEST'
            ),
            '.', LPAD(v_material_id, 6, '0')
        );
        SET v_material_name = ELT(MOD(v_material_id - 1, 32) + 1,
            '贴片电阻', 'MLCC贴片电容', 'TVS瞬态抑制二极管', '电源管理芯片',
            '隔离光耦', '霍尔电流传感器', 'CAN收发器', 'NTC温度传感器',
            '高压互锁连接器', 'M12航空插座', '接线端子', '连接器胶芯',
            '高压屏蔽线缆', 'BMS采样线束', '低压控制线束', '铜编织软连接',
            '压铸铝壳体', '阳极氧化铝型材', 'T2紫铜排', '不锈钢内六角螺钉',
            '硅胶密封圈', '深沟球轴承', '导热硅脂', '液冷板钎焊毛坯',
            '环氧灌封胶', '三防漆', '阻燃塑胶粒', '防静电吸塑托盘',
            '五层加强纸箱', '伺服电机', '直流无刷风机', '老化测试治具'
        );
        SET v_specification = ELT(MOD(v_material_id - 1, 32) + 1,
            CONCAT('R0603 10kΩ ±1% ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('C0603 100nF X7R 50V ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SMBJ58CA 600W ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('QFN-32 5V/3.3V DCDC ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SOP-4 CTR100-200% ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('0-500A 5V闭环 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('CAN FD 5Mbps SOIC-8 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('10k B3435 环氧头 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('800V 2芯 HVIL 橙色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('M12 A-Coding 4Pin IP67 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SXA-001T-P0.6 镀锡 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('PA66-GF30 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('EVRP 35mm2 橙色屏蔽 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('24AWG 16芯 600mm ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('AVSS 0.5mm2 12芯 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('T2 25x3x180 镀锡 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('ADC12 压铸 喷粉 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('6063-T5 40x40 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('T2 30x5 镀镍 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('M4x12 304 达克罗 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('VMQ 70A 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('6202-2RS C3 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('3.0W/mK 灰色 1kg/罐 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('AL3003 12通道 460x180 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('双组份 阻燃UL94-V0 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('丙烯酸透明 快干 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('PA66 V0 黑色 25kg/包 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('600x400x35 ESD ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('520x380x260 AB楞 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('750W 3000rpm 绝对值编码器 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('24V 120x120x38 PWM ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('8通道 0-1000V 夹具 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0'))
        );
        SET v_unit = ELT(MOD(v_material_id - 1, 32) + 1,
            'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs',
            'pcs', 'pcs', 'pcs', 'pcs', 'm', 'pcs', 'pcs', 'pcs',
            'pcs', 'm', 'pcs', 'pcs', 'pcs', 'pcs', 'kg', 'pcs',
            'kg', 'L', 'kg', 'pcs', 'pcs', 'pcs', 'pcs', '套'
        );
        SET v_status_rand = RAND();
        SET v_order_status = CASE
            WHEN v_status_rand < 0.68 THEN '关闭'
            WHEN v_status_rand < 0.90 THEN '执行中'
            ELSE '已下单'
        END;
        SET v_po_month = DATE_ADD('2023-01-01', INTERVAL FLOOR(RAND() * 42) MONTH);

        SET v_purchase_qty = CASE v_unit
            WHEN 'pcs' THEN ROUND(500 + RAND() * 50000, 4)
            WHEN 'kg' THEN ROUND(50 + RAND() * 5000, 4)
            WHEN 'm' THEN ROUND(100 + RAND() * 20000, 4)
            ELSE ROUND(20 + RAND() * 3000, 4)
        END;

        SET v_delivery_rate = CASE v_supplier_level
            WHEN 'A' THEN 0.94 + RAND() * 0.08
            WHEN 'B' THEN 0.86 + RAND() * 0.13
            WHEN 'C' THEN 0.74 + RAND() * 0.18
            ELSE 0.58 + RAND() * 0.25
        END;

        IF v_order_status = '已下单' THEN
            SET v_delivery_rate = RAND() * 0.15;
        ELSEIF v_order_status = '执行中' THEN
            SET v_delivery_rate = v_delivery_rate * (0.55 + RAND() * 0.35);
        END IF;

        SET v_delivery_qty = ROUND(LEAST(v_purchase_qty, v_purchase_qty * v_delivery_rate), 4);
        SET v_receipt_qty = ROUND(v_delivery_qty * (0.985 + RAND() * 0.015), 4);
        SET v_quality_rate = CASE v_supplier_level
            WHEN 'A' THEN RAND() * 0.012
            WHEN 'B' THEN 0.005 + RAND() * 0.025
            WHEN 'C' THEN 0.020 + RAND() * 0.055
            ELSE 0.050 + RAND() * 0.090
        END;
        SET v_unqualified_qty = IF(RAND() < 0.35, 0, ROUND(v_receipt_qty * v_quality_rate, 4));
        SET v_qualified_qty = GREATEST(v_receipt_qty - v_unqualified_qty, 0);
        SET v_concession_qty = IF(v_unqualified_qty > 0 AND RAND() < 0.45, ROUND(v_unqualified_qty * (0.20 + RAND() * 0.50), 4), 0);
        SET v_warehousing_qty = ROUND(LEAST(v_receipt_qty, v_qualified_qty + v_concession_qty), 4);
        SET v_shortage_qty = ROUND(GREATEST(v_purchase_qty - v_delivery_qty, 0), 4);
        SET v_late_probability = CASE v_supplier_level
            WHEN 'A' THEN 0.06
            WHEN 'B' THEN 0.12
            WHEN 'C' THEN 0.22
            ELSE 0.35
        END;
        SET v_delayed_qty = IF(v_order_status <> '已下单' AND RAND() < v_late_probability,
            ROUND(v_delivery_qty * (0.10 + RAND() * 0.45), 4),
            0
        );
        SET v_delivery_status = CASE
            WHEN v_order_status = '已下单' THEN '待确认'
            WHEN v_delayed_qty > 0 OR v_unqualified_qty > v_receipt_qty * 0.05 THEN '异常'
            WHEN v_order_status = '关闭' AND v_shortage_qty > v_purchase_qty * 0.12 THEN '异常'
            ELSE '正常'
        END;

        INSERT INTO procurement_supply_detail (
            supplier_code, supplier_name, purchase_order_no, material_code,
            material_name, specification, material_unit, purchase_quantity,
            supplier_delivery_quantity, receipt_quantity,
            inspection_qualified_quantity, inspection_unqualified_quantity,
            concession_quantity, warehousing_quantity, delayed_receipt_quantity,
            supply_shortage_quantity, order_status, supplier_level, delivery_status
        ) VALUES (
            v_supplier_code, v_supplier_name,
            CONCAT('CGDD', DATE_FORMAT(v_po_month, '%Y%m'), LPAD(v_i + 1000, 6, '0')),
            v_material_code, v_material_name, v_specification, v_unit,
            v_purchase_qty, v_delivery_qty, v_receipt_qty, v_qualified_qty,
            v_unqualified_qty, v_concession_qty, v_warehousing_qty,
            v_delayed_qty, v_shortage_qty, v_order_status, v_supplier_level, v_delivery_status
        );

        SET v_i = v_i + 1;
    END WHILE;
END$$

-- =============================================
-- 3. 采购供应汇报表 - 从采购明细聚合生成
-- =============================================
DROP PROCEDURE IF EXISTS build_real_procurement_supply_report$$
CREATE PROCEDURE build_real_procurement_supply_report()
BEGIN
    TRUNCATE TABLE procurement_supply_report;

    INSERT INTO procurement_supply_report (
        supplier_code, supplier_name, material_code, material_name,
        specification, material_unit, purchase_quantity,
        supplier_delivery_quantity, receipt_quantity,
        inspection_qualified_quantity, inspection_unqualified_quantity,
        concession_quantity, warehousing_quantity, delayed_receipt_quantity,
        supply_shortage_quantity, on_time_delivery_times, delayed_delivery_times
    )
    SELECT
        supplier_code,
        supplier_name,
        material_code,
        material_name,
        specification,
        material_unit,
        ROUND(SUM(IFNULL(purchase_quantity, 0)), 4),
        ROUND(SUM(IFNULL(supplier_delivery_quantity, 0)), 4),
        ROUND(SUM(IFNULL(receipt_quantity, 0)), 4),
        ROUND(SUM(IFNULL(inspection_qualified_quantity, 0)), 4),
        ROUND(SUM(IFNULL(inspection_unqualified_quantity, 0)), 4),
        ROUND(SUM(IFNULL(concession_quantity, 0)), 4),
        ROUND(SUM(IFNULL(warehousing_quantity, 0)), 4),
        ROUND(SUM(IFNULL(delayed_receipt_quantity, 0)), 4),
        ROUND(SUM(IFNULL(supply_shortage_quantity, 0)), 4),
        SUM(CASE WHEN delivery_status = '正常' THEN 1 ELSE 0 END),
        SUM(CASE WHEN delivery_status <> '正常' THEN 1 ELSE 0 END)
    FROM procurement_supply_detail
    GROUP BY supplier_code, supplier_name, material_code, material_name, specification, material_unit;
END$$

-- =============================================
-- 4. 售后记录统计表 - 生成2026年1-5月售后统计
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_after_sales_statistics$$
CREATE PROCEDURE generate_real_after_sales_statistics()
BEGIN
    DECLARE v_period_date DATE DEFAULT '2026-01-01';
    DECLARE v_product_id INT;
    DECLARE v_product_type VARCHAR(50);
    DECLARE v_base INT;
    DECLARE v_season_factor DECIMAL(10,4);
    DECLARE v_quality_factor DECIMAL(10,4);
    DECLARE v_pending_rate DECIMAL(10,4);
    DECLARE v_complaint INT;
    DECLARE v_return_exchange INT;
    DECLARE v_consultation INT;
    DECLARE v_other INT;
    DECLARE v_total INT;
    DECLARE v_pending INT;
    DECLARE v_completed INT;

    TRUNCATE TABLE after_sales_statistics;

    WHILE v_period_date <= '2026-05-01' DO
        SET v_product_id = 1;
        WHILE v_product_id <= 10 DO
            SET v_product_type = ELT(v_product_id,
                '智能控制器', '工业传感器', '动力电池模组', '车载连接器', '伺服驱动器',
                '高压线束', '精密结构件', '通信网关', '液冷板组件', '电源管理模块'
            );
            SET v_base = CASE v_product_id
                WHEN 1 THEN 180 WHEN 2 THEN 135 WHEN 3 THEN 150 WHEN 4 THEN 120
                WHEN 5 THEN 105 WHEN 6 THEN 96 WHEN 7 THEN 88 WHEN 8 THEN 82
                WHEN 9 THEN 88 ELSE 130
            END;
            SET v_season_factor = CASE
                WHEN MONTH(v_period_date) = 1 THEN 0.78
                WHEN MONTH(v_period_date) = 2 THEN 0.62
                WHEN MONTH(v_period_date) = 3 THEN 1.05
                WHEN MONTH(v_period_date) = 4 THEN 1.12
                WHEN MONTH(v_period_date) = 5 THEN 1.18
                ELSE 1.00
            END;
            SET v_quality_factor = CASE
                WHEN v_product_id IN (3, 4, 6, 10) THEN 1.25
                WHEN v_product_id IN (1, 2, 9) THEN 1.05
                WHEN v_product_id IN (7, 8) THEN 0.82
                ELSE 1.00
            END;
            SET v_pending_rate = CASE
                WHEN MONTH(v_period_date) = 1 THEN 0.010 + RAND() * 0.020
                WHEN MONTH(v_period_date) = 2 THEN 0.015 + RAND() * 0.030
                WHEN MONTH(v_period_date) = 3 THEN 0.025 + RAND() * 0.045
                WHEN MONTH(v_period_date) = 4 THEN 0.060 + RAND() * 0.050
                ELSE 0.090 + RAND() * 0.070
            END;
            SET v_complaint = GREATEST(1, FLOOR(v_base * v_season_factor * v_quality_factor * (0.030 + RAND() * 0.035)));
            SET v_return_exchange = FLOOR(v_complaint * (0.12 + RAND() * IF(v_product_id IN (3, 4, 6, 10), 0.38, 0.26)));
            SET v_consultation = FLOOR(v_base * v_season_factor * (0.58 + RAND() * 0.28));
            SET v_other = FLOOR(v_base * v_season_factor * (0.045 + RAND() * 0.075));
            SET v_total = v_complaint + v_return_exchange + v_consultation + v_other;
            SET v_pending = FLOOR(v_total * v_pending_rate);
            SET v_completed = v_total - v_pending;

            INSERT INTO after_sales_statistics (
                product_type, year, period, complaint_times, return_exchange_times,
                consultation_times, other_record_times, total_service_applications,
                completed_times, pending_times, completion_rate
            ) VALUES (
                v_product_type, CONCAT(YEAR(v_period_date), '年'), CONCAT(MONTH(v_period_date), '月'),
                v_complaint, v_return_exchange, v_consultation, v_other,
                v_total, v_completed, v_pending, CONCAT(ROUND(v_completed / v_total * 100, 0), '%')
            );

            SET v_product_id = v_product_id + 1;
        END WHILE;

        SET v_period_date = DATE_ADD(v_period_date, INTERVAL 1 MONTH);
    END WHILE;
END$$

-- =============================================
-- 5. 设备维修记录统计表 - 生成960条年度设备运行数据
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_equipment_maintenance$$
CREATE PROCEDURE generate_real_equipment_maintenance()
BEGIN
    DECLARE v_year INT DEFAULT 2019;
    DECLARE v_type_id INT;
    DECLARE v_line_id INT;
    DECLARE v_equipment_type VARCHAR(100);
    DECLARE v_quantity INT;
    DECLARE v_normal_hours DECIMAL(10,2);
    DECLARE v_fault_hours DECIMAL(10,2);
    DECLARE v_fault_times INT;
    DECLARE v_invalid_hours DECIMAL(10,2);
    DECLARE v_total_hours DECIMAL(10,2);
    DECLARE v_mttr DECIMAL(10,2);
    DECLARE v_mttf DECIMAL(10,2);
    DECLARE v_mtbf DECIMAL(10,2);

    WHILE v_year <= 2026 DO
        SET v_type_id = 1;
        WHILE v_type_id <= 12 DO
            SET v_equipment_type = ELT(v_type_id,
                '数车加工中心', '注塑成型机', '全自动点胶机', 'CNC加工中心',
                '激光切割机', '冲压机', '焊接机器人', '自动化流水线',
                'AOI检测设备', '老化测试柜', '贴片机', '回流焊炉'
            );
            SET v_line_id = 1;
            WHILE v_line_id <= 10 DO
                SET v_quantity = FLOOR(3 + RAND() * 28);
                SET v_normal_hours = ROUND(v_quantity * (1800 + RAND() * 2600), 2);
                SET v_fault_hours = ROUND(v_normal_hours * (0.002 + RAND() * 0.030), 2);
                SET v_fault_times = GREATEST(1, ROUND(v_fault_hours / (1.0 + RAND() * 4.0), 0));
                SET v_invalid_hours = ROUND(v_quantity * (20 + RAND() * 180), 2);
                SET v_total_hours = ROUND(v_normal_hours + v_fault_hours, 2);
                SET v_mttr = ROUND(v_fault_hours / v_fault_times, 2);
                SET v_mttf = ROUND(v_normal_hours / v_fault_times, 2);
                SET v_mtbf = ROUND((v_normal_hours + v_fault_hours) / v_fault_times, 2);

                INSERT INTO equipment_maintenance_statistics (
                    comparison_year, equipment_type, equipment_quantity,
                    normal_operation_hours, fault_hours, fault_times,
                    total_hours, invalid_hours, mttr, mttf, mtbf
                ) VALUES (
                    CONCAT(v_year, '年'), v_equipment_type, v_quantity,
                    v_normal_hours, v_fault_hours, v_fault_times,
                    v_total_hours, v_invalid_hours, v_mttr, v_mttf, v_mtbf
                );

                SET v_line_id = v_line_id + 1;
            END WHILE;

            SET v_type_id = v_type_id + 1;
        END WHILE;

        SET v_year = v_year + 1;
    END WHILE;
END$$

-- =============================================
-- 6. 统计样本表 - 生成10000条带过程漂移的测量样本
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_statistical_sample$$
CREATE PROCEDURE generate_real_statistical_sample()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_group_id INT;
    DECLARE v_base DECIMAL(10,4);
    DECLARE v_shift DECIMAL(10,4);

    WHILE v_i < 10000 DO
        SET v_group_id = FLOOR(1 + RAND() * 100);
        SET v_base = 48 + MOD(v_group_id, 12) * 0.35 + RAND() * 1.20;
        SET v_shift = (RAND() + RAND() + RAND() - 1.5) * 1.80;

        INSERT INTO statistical_sample (
            sample_group, X1, X2, X3, X4, X5, X6, X7, X8
        ) VALUES (
            CONCAT('产线', LPAD(v_group_id, 3, '0')),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.80, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.70, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.90, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 2.10, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.60, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.80, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 2.00, 4),
            ROUND(v_base + v_shift + (RAND() - 0.5) * 1.70, 4)
        );

        SET v_i = v_i + 1;
    END WHILE;
END$$

-- =============================================
-- 7. 物料供需平衡明细表 - 生成15000条MRP供需明细
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_material_supply_detail$$
CREATE PROCEDURE generate_real_material_supply_detail()
BEGIN
    DECLARE v_i INT DEFAULT 0;
    DECLARE v_target_count INT DEFAULT 15000;
    DECLARE v_material_id INT;
    DECLARE v_product_id INT;
    DECLARE v_material_code VARCHAR(100);
    DECLARE v_material_name VARCHAR(200);
    DECLARE v_specification VARCHAR(200);
    DECLARE v_unit VARCHAR(50);
    DECLARE v_material_group VARCHAR(20);
    DECLARE v_demand_qty DECIMAL(18,4);
    DECLARE v_inventory_qty DECIMAL(18,4);
    DECLARE v_purchase_qty DECIMAL(18,4);
    DECLARE v_wip_qty DECIMAL(18,4);
    DECLARE v_outsource_qty DECIMAL(18,4);
    DECLARE v_reserve_qty DECIMAL(18,4);
    DECLARE v_other_qty DECIMAL(18,4);
    DECLARE v_available_qty DECIMAL(18,4);
    DECLARE v_forecast_supply DECIMAL(18,4);
    DECLARE v_demand_date DATE;

    WHILE v_i < v_target_count DO
        SET v_material_id = FLOOR(1 + RAND() * 192);
        SET v_product_id = FLOOR(1 + RAND() * 10);
        SET v_material_group = ELT(MOD(v_material_id - 1, 32) + 1,
            'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC', 'ELEC',
            'CONN', 'CONN', 'CONN', 'CONN', 'CABLE', 'CABLE', 'CABLE', 'CABLE',
            'MECH', 'MECH', 'MECH', 'MECH', 'MECH', 'MECH', 'THERM', 'THERM',
            'CHEM', 'CHEM', 'CHEM', 'PACK', 'PACK', 'MOTOR', 'MOTOR', 'TEST'
        );
        SET v_material_code = CONCAT(v_material_group, '.', LPAD(v_material_id, 6, '0'));
        SET v_material_name = ELT(MOD(v_material_id - 1, 32) + 1,
            '贴片电阻', 'MLCC贴片电容', 'TVS瞬态抑制二极管', '电源管理芯片',
            '隔离光耦', '霍尔电流传感器', 'CAN收发器', 'NTC温度传感器',
            '高压互锁连接器', 'M12航空插座', '接线端子', '连接器胶芯',
            '高压屏蔽线缆', 'BMS采样线束', '低压控制线束', '铜编织软连接',
            '压铸铝壳体', '阳极氧化铝型材', 'T2紫铜排', '不锈钢内六角螺钉',
            '硅胶密封圈', '深沟球轴承', '导热硅脂', '液冷板钎焊毛坯',
            '环氧灌封胶', '三防漆', '阻燃塑胶粒', '防静电吸塑托盘',
            '五层加强纸箱', '伺服电机', '直流无刷风机', '老化测试治具'
        );
        SET v_specification = ELT(MOD(v_material_id - 1, 32) + 1,
            CONCAT('R0603 10kΩ ±1% ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('C0603 100nF X7R 50V ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SMBJ58CA 600W ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('QFN-32 5V/3.3V DCDC ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SOP-4 CTR100-200% ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('0-500A 5V闭环 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('CAN FD 5Mbps SOIC-8 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('10k B3435 环氧头 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('800V 2芯 HVIL 橙色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('M12 A-Coding 4Pin IP67 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('SXA-001T-P0.6 镀锡 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('PA66-GF30 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('EVRP 35mm2 橙色屏蔽 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('24AWG 16芯 600mm ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('AVSS 0.5mm2 12芯 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('T2 25x3x180 镀锡 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('ADC12 压铸 喷粉 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('6063-T5 40x40 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('T2 30x5 镀镍 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('M4x12 304 达克罗 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('VMQ 70A 黑色 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('6202-2RS C3 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('3.0W/mK 灰色 1kg/罐 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('AL3003 12通道 460x180 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('双组份 阻燃UL94-V0 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('丙烯酸透明 快干 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('PA66 V0 黑色 25kg/包 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('600x400x35 ESD ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('520x380x260 AB楞 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('750W 3000rpm 绝对值编码器 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('24V 120x120x38 PWM ', LPAD(MOD(v_material_id, 99) + 1, 2, '0')),
            CONCAT('8通道 0-1000V 夹具 ', LPAD(MOD(v_material_id, 99) + 1, 2, '0'))
        );
        SET v_unit = ELT(MOD(v_material_id - 1, 32) + 1,
            'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs', 'pcs',
            'pcs', 'pcs', 'pcs', 'pcs', 'm', 'pcs', 'pcs', 'pcs',
            'pcs', 'm', 'pcs', 'pcs', 'pcs', 'pcs', 'kg', 'pcs',
            'kg', 'L', 'kg', 'pcs', 'pcs', 'pcs', 'pcs', '套'
        );
        SET v_demand_date = DATE_ADD('2024-01-01', INTERVAL FLOOR(RAND() * 900) DAY);
        SET v_demand_qty = CASE v_unit
            WHEN 'pcs' THEN ROUND(50 + RAND() * 18000, 4)
            WHEN 'kg' THEN ROUND(10 + RAND() * 3500, 4)
            WHEN 'm' THEN ROUND(20 + RAND() * 12000, 4)
            WHEN 'L' THEN ROUND(5 + RAND() * 1800, 4)
            ELSE ROUND(1 + RAND() * 600, 4)
        END;
        SET v_inventory_qty = ROUND(v_demand_qty * (0.05 + RAND() * 0.70), 4);
        SET v_purchase_qty = ROUND(v_demand_qty * RAND() * 0.90, 4);
        SET v_wip_qty = ROUND(v_demand_qty * RAND() * 0.45, 4);
        SET v_outsource_qty = ROUND(v_demand_qty * RAND() * 0.30, 4);
        SET v_reserve_qty = ROUND(v_demand_qty * RAND() * 0.50, 4);
        SET v_other_qty = ROUND(v_demand_qty * RAND() * 0.15, 4);
        SET v_forecast_supply = ROUND(v_purchase_qty + v_wip_qty + v_outsource_qty + v_reserve_qty + v_other_qty, 4);
        SET v_available_qty = ROUND(
            v_inventory_qty + v_purchase_qty * 0.65 + v_wip_qty * 0.50 +
            v_outsource_qty * 0.45 + v_reserve_qty * 0.80 + v_other_qty * 0.60,
            4
        );

        INSERT INTO material_supply_detail (
            demand_order_no, product_code, product_name, bom_no,
            material_code, material_name, specification, material_unit,
            demand_date, demand_quantity, available_quantity,
            shortage_quantity, forecast_supply, forecast_days,
            inventory_quantity, purchase_in_transit_quantity,
            production_in_process_quantity, outsourcing_quantity,
            supplier_reserve_quantity, other_supply_quantity
        ) VALUES (
            CONCAT('DD', DATE_FORMAT(v_demand_date, '%Y%m'), LPAD(v_i + 1000, 6, '0')),
            ELT(v_product_id,
                'Y01.0130', 'Y01.0131', 'Y02.0201', 'Y02.0202', 'Y03.0310',
                'Y03.0311', 'Y04.0408', 'Y04.0412', 'Y05.0506', 'Y05.0518'
            ),
            ELT(v_product_id,
                '智能控制器', '工业传感器', '动力电池模组', '车载连接器', '伺服驱动器',
                '高压线束', '精密结构件', '通信网关', '液冷板组件', '电源管理模块'
            ),
            CONCAT('BOM', LPAD(v_product_id, 4, '0'), '-', LPAD(MOD(v_material_id, 20) + 1, 2, '0')),
            v_material_code, v_material_name, v_specification, v_unit,
            v_demand_date, v_demand_qty, v_available_qty,
            ROUND(GREATEST(v_demand_qty - v_available_qty, 0), 4),
            v_forecast_supply, ELT(FLOOR(1 + RAND() * 5), 7, 14, 21, 30, 45),
            v_inventory_qty, v_purchase_qty, v_wip_qty, v_outsource_qty,
            v_reserve_qty, v_other_qty
        );

        SET v_i = v_i + 1;
    END WHILE;
END$$

-- =============================================
-- 8. 物料供需平衡汇总表 - 从明细聚合生成
-- =============================================
DROP PROCEDURE IF EXISTS build_real_material_supply_summary$$
CREATE PROCEDURE build_real_material_supply_summary()
BEGIN
    TRUNCATE TABLE material_supply_summary;

    INSERT INTO material_supply_summary (
        material_code, material_name, specification, material_unit,
        demand_date, demand_quantity, available_quantity,
        shortage_quantity, forecast_supply, inventory_quantity,
        purchase_in_transit_quantity, production_in_process_quantity,
        outsourcing_quantity, supplier_reserve_quantity, other_supply_quantity
    )
    SELECT
        material_code,
        material_name,
        specification,
        material_unit,
        demand_date,
        ROUND(SUM(IFNULL(demand_quantity, 0)), 4),
        ROUND(SUM(IFNULL(available_quantity, 0)), 4),
        ROUND(GREATEST(SUM(IFNULL(demand_quantity, 0)) - SUM(IFNULL(available_quantity, 0)), 0), 4),
        ROUND(SUM(IFNULL(forecast_supply, 0)), 4),
        ROUND(SUM(IFNULL(inventory_quantity, 0)), 4),
        ROUND(SUM(IFNULL(purchase_in_transit_quantity, 0)), 4),
        ROUND(SUM(IFNULL(production_in_process_quantity, 0)), 4),
        ROUND(SUM(IFNULL(outsourcing_quantity, 0)), 4),
        ROUND(SUM(IFNULL(supplier_reserve_quantity, 0)), 4),
        ROUND(SUM(IFNULL(other_supply_quantity, 0)), 4)
    FROM material_supply_detail
    GROUP BY material_code, material_name, specification, material_unit, demand_date;
END$$

DELIMITER ;

-- =============================================
-- 执行增强版样本数据生成
-- =============================================
CALL generate_real_customer_order_forecast();
CALL generate_real_procurement_supply_detail();
CALL build_real_procurement_supply_report();
CALL generate_real_after_sales_statistics();
CALL generate_real_equipment_maintenance();
CALL generate_real_statistical_sample();
CALL generate_real_material_supply_detail();
CALL build_real_material_supply_summary();

-- =============================================
-- 删除增强版存储过程（清理）
-- =============================================
DROP PROCEDURE IF EXISTS generate_real_customer_order_forecast;
DROP PROCEDURE IF EXISTS generate_real_procurement_supply_detail;
DROP PROCEDURE IF EXISTS build_real_procurement_supply_report;
DROP PROCEDURE IF EXISTS generate_real_after_sales_statistics;
DROP PROCEDURE IF EXISTS generate_real_equipment_maintenance;
DROP PROCEDURE IF EXISTS generate_real_statistical_sample;
DROP PROCEDURE IF EXISTS generate_real_material_supply_detail;
DROP PROCEDURE IF EXISTS build_real_material_supply_summary;

-- =============================================
-- 创建跨表联动视图
-- =============================================
DROP VIEW IF EXISTS v_product_month_linkage;
CREATE VIEW v_product_month_linkage AS
SELECT
    c.demand_year_month,
    c.product_code,
    c.product_name,
    c.product_category,
    c.product_unit,
    c.order_quantity,
    c.customer_request_quantity,
    c.actual_delivery_quantity,
    c.available_inventory_quantity,
    IFNULL(m.mrp_demand_quantity, 0) AS mrp_demand_quantity,
    IFNULL(m.mrp_available_quantity, 0) AS mrp_available_quantity,
    IFNULL(m.mrp_shortage_quantity, 0) AS mrp_shortage_quantity,
    IFNULL(m.mrp_material_count, 0) AS mrp_material_count,
    IFNULL(a.total_service_applications, 0) AS total_service_applications,
    IFNULL(a.complaint_times, 0) AS complaint_times,
    IFNULL(a.return_exchange_times, 0) AS return_exchange_times,
    IFNULL(a.completed_times, 0) AS completed_times,
    IFNULL(a.pending_times, 0) AS pending_times
FROM (
    SELECT
        demand_year_month,
        product_code,
        product_name,
        product_category,
        product_unit,
        ROUND(SUM(IFNULL(order_quantity, 0)), 4) AS order_quantity,
        ROUND(SUM(IFNULL(customer_request_quantity, 0)), 4) AS customer_request_quantity,
        ROUND(SUM(IFNULL(actual_delivery_quantity, 0)), 4) AS actual_delivery_quantity,
        ROUND(SUM(IFNULL(available_inventory_quantity, 0)), 4) AS available_inventory_quantity
    FROM customer_order_forecast
    GROUP BY demand_year_month, product_code, product_name, product_category, product_unit
) c
LEFT JOIN (
    SELECT
        DATE_FORMAT(demand_date, '%Y.%m') AS demand_year_month,
        product_code,
        product_name,
        ROUND(SUM(IFNULL(demand_quantity, 0)), 4) AS mrp_demand_quantity,
        ROUND(SUM(IFNULL(available_quantity, 0)), 4) AS mrp_available_quantity,
        ROUND(SUM(IFNULL(shortage_quantity, 0)), 4) AS mrp_shortage_quantity,
        COUNT(DISTINCT material_code) AS mrp_material_count
    FROM material_supply_detail
    GROUP BY DATE_FORMAT(demand_date, '%Y.%m'), product_code, product_name
) m ON m.demand_year_month = c.demand_year_month
   AND m.product_code = c.product_code
LEFT JOIN (
    SELECT
        CONCAT(REPLACE(year, '年', ''), '.', LPAD(REPLACE(period, '月', ''), 2, '0')) AS demand_year_month,
        product_type,
        SUM(IFNULL(total_service_applications, 0)) AS total_service_applications,
        SUM(IFNULL(complaint_times, 0)) AS complaint_times,
        SUM(IFNULL(return_exchange_times, 0)) AS return_exchange_times,
        SUM(IFNULL(completed_times, 0)) AS completed_times,
        SUM(IFNULL(pending_times, 0)) AS pending_times
    FROM after_sales_statistics
    GROUP BY CONCAT(REPLACE(year, '年', ''), '.', LPAD(REPLACE(period, '月', ''), 2, '0')), product_type
) a ON a.demand_year_month = c.demand_year_month
   AND a.product_type = c.product_name;

DROP VIEW IF EXISTS v_material_procurement_linkage;
CREATE VIEW v_material_procurement_linkage AS
SELECT
    m.material_code,
    m.material_name,
    m.specification,
    m.material_unit,
    m.mrp_demand_quantity,
    m.mrp_available_quantity,
    m.mrp_shortage_quantity,
    m.mrp_product_count,
    IFNULL(p.purchase_quantity, 0) AS purchase_quantity,
    IFNULL(p.supplier_delivery_quantity, 0) AS supplier_delivery_quantity,
    IFNULL(p.receipt_quantity, 0) AS receipt_quantity,
    IFNULL(p.warehousing_quantity, 0) AS warehousing_quantity,
    IFNULL(p.supply_shortage_quantity, 0) AS supply_shortage_quantity,
    IFNULL(p.supplier_count, 0) AS supplier_count,
    IFNULL(p.abnormal_delivery_times, 0) AS abnormal_delivery_times
FROM (
    SELECT
        material_code,
        material_name,
        specification,
        material_unit,
        ROUND(SUM(IFNULL(demand_quantity, 0)), 4) AS mrp_demand_quantity,
        ROUND(SUM(IFNULL(available_quantity, 0)), 4) AS mrp_available_quantity,
        ROUND(SUM(IFNULL(shortage_quantity, 0)), 4) AS mrp_shortage_quantity,
        COUNT(DISTINCT product_code) AS mrp_product_count
    FROM material_supply_detail
    GROUP BY material_code, material_name, specification, material_unit
) m
LEFT JOIN (
    SELECT
        material_code,
        material_name,
        specification,
        material_unit,
        ROUND(SUM(IFNULL(purchase_quantity, 0)), 4) AS purchase_quantity,
        ROUND(SUM(IFNULL(supplier_delivery_quantity, 0)), 4) AS supplier_delivery_quantity,
        ROUND(SUM(IFNULL(receipt_quantity, 0)), 4) AS receipt_quantity,
        ROUND(SUM(IFNULL(warehousing_quantity, 0)), 4) AS warehousing_quantity,
        ROUND(SUM(IFNULL(supply_shortage_quantity, 0)), 4) AS supply_shortage_quantity,
        COUNT(DISTINCT supplier_code) AS supplier_count,
        SUM(CASE WHEN delivery_status = '异常' THEN 1 ELSE 0 END) AS abnormal_delivery_times
    FROM procurement_supply_detail
    GROUP BY material_code, material_name, specification, material_unit
) p ON p.material_code = m.material_code
   AND p.material_name = m.material_name
   AND p.specification = m.specification
   AND p.material_unit = m.material_unit;

-- =============================================
-- 验证数据量
-- =============================================
SELECT 'customer_order_forecast' AS table_name, COUNT(*) AS record_count FROM customer_order_forecast
UNION ALL
SELECT 'procurement_supply_detail', COUNT(*) FROM procurement_supply_detail
UNION ALL
SELECT 'procurement_supply_report', COUNT(*) FROM procurement_supply_report
UNION ALL
SELECT 'after_sales_statistics', COUNT(*) FROM after_sales_statistics
UNION ALL
SELECT 'equipment_maintenance_statistics', COUNT(*) FROM equipment_maintenance_statistics
UNION ALL
SELECT 'statistical_sample', COUNT(*) FROM statistical_sample
UNION ALL
SELECT 'material_supply_detail', COUNT(*) FROM material_supply_detail
UNION ALL
SELECT 'material_supply_summary', COUNT(*) FROM material_supply_summary;

-- =============================================
-- 业务一致性校验（abnormal_count 应为 0）
-- =============================================
SELECT 'customer_order_forecast.deviation_check' AS check_item, COUNT(*) AS abnormal_count
FROM customer_order_forecast
WHERE ROUND(IFNULL(order_forecast_deviation, 0), 4) <> ROUND(IFNULL(order_quantity, 0) - IFNULL(internal_forecast_quantity, 0), 4)
   OR ROUND(IFNULL(order_request_deviation, 0), 4) <> ROUND(IFNULL(order_quantity, 0) - IFNULL(customer_request_quantity, 0), 4)
   OR ROUND(IFNULL(order_delivery_deviation, 0), 4) <> ROUND(IFNULL(order_quantity, 0) - IFNULL(actual_delivery_quantity, 0), 4)
UNION ALL
SELECT 'customer_order_forecast.quantity_chain_check', COUNT(*)
FROM customer_order_forecast
WHERE IFNULL(actual_delivery_quantity, 0) > IFNULL(customer_request_quantity, 0) + 0.0001
   OR IFNULL(actual_delivery_quantity, 0) > IFNULL(order_quantity, 0) + 0.0001
   OR IFNULL(available_inventory_quantity, 0) < 0
UNION ALL
SELECT 'procurement_supply_detail.quantity_chain_check', COUNT(*)
FROM procurement_supply_detail
WHERE IFNULL(receipt_quantity, 0) > IFNULL(supplier_delivery_quantity, 0) + 0.0001
   OR IFNULL(warehousing_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(inspection_qualified_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(inspection_unqualified_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(delayed_receipt_quantity, 0) > IFNULL(purchase_quantity, 0) + 0.0001
   OR IFNULL(supply_shortage_quantity, 0) > IFNULL(purchase_quantity, 0) + 0.0001
UNION ALL
SELECT 'procurement_supply_report.quantity_chain_check', COUNT(*)
FROM procurement_supply_report
WHERE IFNULL(receipt_quantity, 0) > IFNULL(supplier_delivery_quantity, 0) + 0.0001
   OR IFNULL(warehousing_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(inspection_qualified_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(inspection_unqualified_quantity, 0) > IFNULL(receipt_quantity, 0) + 0.0001
   OR IFNULL(delayed_receipt_quantity, 0) > IFNULL(purchase_quantity, 0) + 0.0001
   OR IFNULL(supply_shortage_quantity, 0) > IFNULL(purchase_quantity, 0) + 0.0001
UNION ALL
SELECT 'after_sales_statistics.total_check', COUNT(*)
FROM after_sales_statistics
WHERE IFNULL(complaint_times, 0) + IFNULL(return_exchange_times, 0) + IFNULL(consultation_times, 0) + IFNULL(other_record_times, 0) <> IFNULL(total_service_applications, 0)
   OR IFNULL(completed_times, 0) + IFNULL(pending_times, 0) <> IFNULL(total_service_applications, 0)
   OR completion_rate <> CONCAT(ROUND(IFNULL(completed_times, 0) / NULLIF(total_service_applications, 0) * 100, 0), '%')
UNION ALL
SELECT 'equipment_maintenance_statistics.metric_check', COUNT(*)
FROM equipment_maintenance_statistics
WHERE IFNULL(total_hours, 0) + 0.0001 < IFNULL(normal_operation_hours, 0) + IFNULL(fault_hours, 0)
   OR IFNULL(fault_times, 0) <= 0
   OR ABS(IFNULL(mttr, 0) - ROUND(IFNULL(fault_hours, 0) / NULLIF(fault_times, 0), 2)) > 0.05
   OR ABS(IFNULL(mttf, 0) - ROUND(IFNULL(normal_operation_hours, 0) / NULLIF(fault_times, 0), 2)) > 0.05
   OR ABS(IFNULL(mtbf, 0) - ROUND((IFNULL(normal_operation_hours, 0) + IFNULL(fault_hours, 0)) / NULLIF(fault_times, 0), 2)) > 0.05
UNION ALL
SELECT 'statistical_sample.range_check', COUNT(*)
FROM statistical_sample
WHERE X1 NOT BETWEEN 35 AND 65
   OR X2 NOT BETWEEN 35 AND 65
   OR X3 NOT BETWEEN 35 AND 65
   OR X4 NOT BETWEEN 35 AND 65
   OR X5 NOT BETWEEN 35 AND 65
   OR X6 NOT BETWEEN 35 AND 65
   OR X7 NOT BETWEEN 35 AND 65
   OR X8 NOT BETWEEN 35 AND 65
UNION ALL
SELECT 'material_supply_detail.shortage_check', COUNT(*)
FROM material_supply_detail
WHERE ROUND(IFNULL(shortage_quantity, 0), 4) <> ROUND(GREATEST(IFNULL(demand_quantity, 0) - IFNULL(available_quantity, 0), 0), 4)
UNION ALL
SELECT 'material_supply_summary.shortage_check', COUNT(*)
FROM material_supply_summary
WHERE ROUND(IFNULL(shortage_quantity, 0), 4) <> ROUND(GREATEST(IFNULL(demand_quantity, 0) - IFNULL(available_quantity, 0), 0), 4)
UNION ALL
SELECT 'master_data.customer_code_name_check', COUNT(*)
FROM (
    SELECT customer_code
    FROM customer_order_forecast
    GROUP BY customer_code
    HAVING COUNT(DISTINCT customer_name) > 1
) t
UNION ALL
SELECT 'master_data.product_code_name_check', COUNT(*)
FROM (
    SELECT product_code
    FROM customer_order_forecast
    GROUP BY product_code
    HAVING COUNT(DISTINCT product_name) > 1
) t
UNION ALL
SELECT 'master_data.supplier_code_name_check', COUNT(*)
FROM (
    SELECT supplier_code
    FROM procurement_supply_detail
    GROUP BY supplier_code
    HAVING COUNT(DISTINCT supplier_name) > 1
) t
UNION ALL
SELECT 'master_data.proc_material_code_name_check', COUNT(*)
FROM (
    SELECT material_code
    FROM procurement_supply_detail
    GROUP BY material_code
    HAVING COUNT(DISTINCT material_name) > 1
) t
UNION ALL
SELECT 'master_data.mrp_material_code_name_check', COUNT(*)
FROM (
    SELECT material_code
    FROM material_supply_detail
    GROUP BY material_code
    HAVING COUNT(DISTINCT material_name) > 1
) t
UNION ALL
SELECT 'linkage.order_product_to_mrp_check', COUNT(*)
FROM (
    SELECT DISTINCT c.product_code
    FROM customer_order_forecast c
    LEFT JOIN material_supply_detail m ON m.product_code = c.product_code
    WHERE m.product_code IS NULL
) t
UNION ALL
SELECT 'linkage.mrp_product_to_order_check', COUNT(*)
FROM (
    SELECT DISTINCT m.product_code
    FROM material_supply_detail m
    LEFT JOIN customer_order_forecast c ON c.product_code = m.product_code
    WHERE c.product_code IS NULL
) t
UNION ALL
SELECT 'linkage.proc_material_to_mrp_check', COUNT(*)
FROM (
    SELECT DISTINCT p.material_code
    FROM procurement_supply_detail p
    LEFT JOIN material_supply_detail m ON m.material_code = p.material_code
    WHERE m.material_code IS NULL
) t
UNION ALL
SELECT 'linkage.mrp_material_to_proc_check', COUNT(*)
FROM (
    SELECT DISTINCT m.material_code
    FROM material_supply_detail m
    LEFT JOIN procurement_supply_detail p ON p.material_code = m.material_code
    WHERE p.material_code IS NULL
) t
UNION ALL
SELECT 'linkage.after_sales_to_order_product_check', COUNT(*)
FROM (
    SELECT DISTINCT a.product_type
    FROM after_sales_statistics a
    LEFT JOIN customer_order_forecast c ON c.product_name = a.product_type
    WHERE c.product_name IS NULL
) t
UNION ALL
SELECT 'linkage.product_month_view_empty_check', IF(COUNT(*) = 0, 1, 0)
FROM v_product_month_linkage
UNION ALL
SELECT 'linkage.material_procurement_view_empty_check', IF(COUNT(*) = 0, 1, 0)
FROM v_material_procurement_linkage
UNION ALL
SELECT 'linkage.material_procurement_view_unlinked_check', COUNT(*)
FROM (
    SELECT material_code
    FROM v_material_procurement_linkage
    WHERE supplier_count = 0
) t;
