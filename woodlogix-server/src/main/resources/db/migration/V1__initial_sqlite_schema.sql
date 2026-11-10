CREATE TABLE customers (
    id varchar(36) NOT NULL PRIMARY KEY,
    address varchar(255),
    created_at varchar(255) NOT NULL,
    email varchar(255),
    full_name varchar(255) NOT NULL,
    phone varchar(255)
);

CREATE TABLE edge_bandings (
    price_per_meter numeric(15,2) NOT NULL,
    width_mm numeric(10,2) NOT NULL,
    id varchar(36) NOT NULL PRIMARY KEY,
    name varchar(255) NOT NULL
);

CREATE TABLE hardwares (
    unit_price numeric(15,2) NOT NULL,
    id varchar(36) NOT NULL PRIMARY KEY,
    name varchar(255) NOT NULL,
    unit varchar(255) NOT NULL
);

CREATE TABLE materials (
    length_mm numeric(10,2) NOT NULL,
    price_per_sheet numeric(15,2) NOT NULL,
    thickness_mm numeric(10,2) NOT NULL,
    width_mm numeric(10,2) NOT NULL,
    id varchar(36) NOT NULL PRIMARY KEY,
    color varchar(255) NOT NULL,
    name varchar(255) NOT NULL
);

CREATE TABLE project_environments (
    customer_id varchar(36) NOT NULL REFERENCES customers(id),
    id varchar(36) NOT NULL PRIMARY KEY,
    environment_type varchar(255) NOT NULL
);

CREATE TABLE furniture_items (
    depth_mm numeric(10,2) NOT NULL,
    height_mm numeric(10,2) NOT NULL,
    length_mm numeric(10,2) NOT NULL,
    customer_id varchar(36) NOT NULL REFERENCES customers(id),
    environment_id varchar(36) REFERENCES project_environments(id),
    id varchar(36) NOT NULL PRIMARY KEY,
    furniture_type varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    wood_type varchar(255) NOT NULL
);

CREATE TABLE furniture_parts (
    comprimento_mm numeric(10,2) NOT NULL,
    espessura_mm numeric(10,2) NOT NULL,
    largura_mm numeric(10,2) NOT NULL,
    id varchar(36) NOT NULL PRIMARY KEY,
    project_environment_id varchar(36) NOT NULL REFERENCES project_environments(id),
    material varchar(255) NOT NULL,
    nome varchar(255) NOT NULL
);

CREATE TABLE furniture_part_visible_edges (
    furniture_part_id varchar(36) NOT NULL REFERENCES furniture_parts(id),
    visible_edge varchar(255) NOT NULL CHECK (visible_edge IN ('TOP','BOTTOM','LEFT','RIGHT')),
    PRIMARY KEY (furniture_part_id, visible_edge)
);

CREATE TABLE sales_projects (
    total_value numeric(15,2) NOT NULL,
    customer_id varchar(36) NOT NULL REFERENCES customers(id),
    environment_id varchar(36) REFERENCES project_environments(id),
    id varchar(36) NOT NULL PRIMARY KEY,
    stage varchar(40) NOT NULL CHECK (stage IN ('CONTACT','MEASUREMENT_SCHEDULED','QUOTE_SENT','NEGOTIATION','CLOSED')),
    created_at varchar(255) NOT NULL,
    project_name varchar(255) NOT NULL
);

CREATE TABLE project_estimates (
    estimated_hours numeric(10,2) NOT NULL,
    hourly_rate numeric(15,2) NOT NULL,
    machine_rate numeric(15,2) NOT NULL,
    profit_margin numeric(8,3) NOT NULL,
    waste_factor numeric(8,3) NOT NULL,
    id varchar(36) NOT NULL PRIMARY KEY,
    project_id varchar(36) REFERENCES sales_projects(id)
);

CREATE TABLE project_workpieces (
    edge_bottom boolean NOT NULL,
    edge_left boolean NOT NULL,
    edge_right boolean NOT NULL,
    edge_top boolean NOT NULL,
    length_mm numeric(10,2) NOT NULL,
    quantity integer NOT NULL CHECK (quantity >= 1),
    width_mm numeric(10,2) NOT NULL,
    edge_banding_id varchar(36) REFERENCES edge_bandings(id),
    id varchar(36) NOT NULL PRIMARY KEY,
    material_id varchar(36) NOT NULL REFERENCES materials(id),
    project_id varchar(36) NOT NULL REFERENCES sales_projects(id),
    name varchar(255) NOT NULL
);

CREATE TABLE project_estimate_workpieces (
    edge_bottom boolean NOT NULL,
    edge_left boolean NOT NULL,
    edge_right boolean NOT NULL,
    edge_top boolean NOT NULL,
    length_mm numeric(10,2) NOT NULL,
    quantity integer NOT NULL,
    width_mm numeric(10,2) NOT NULL,
    edge_banding_id varchar(36) REFERENCES edge_bandings(id),
    estimate_id varchar(36) NOT NULL REFERENCES project_estimates(id),
    id varchar(36) NOT NULL PRIMARY KEY,
    material_id varchar(36) NOT NULL REFERENCES materials(id),
    project_workpiece_id varchar(36),
    name varchar(255) NOT NULL
);

CREATE TABLE project_estimate_hardwares (
    quantity integer NOT NULL,
    estimate_id varchar(36) NOT NULL REFERENCES project_estimates(id),
    hardware_id varchar(36) NOT NULL REFERENCES hardwares(id),
    id varchar(36) NOT NULL PRIMARY KEY
);
