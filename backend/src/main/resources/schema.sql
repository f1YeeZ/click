CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS mice (
    id UUID PRIMARY KEY,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    variant VARCHAR(100) NOT NULL DEFAULT '',
    slug VARCHAR(180) NOT NULL UNIQUE,
    aliases VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    size_category VARCHAR(20),
    length_mm NUMERIC(7,2),
    width_mm NUMERIC(7,2),
    height_mm NUMERIC(7,2),
    weight_g NUMERIC(7,2),
    shape_type VARCHAR(20),
    hand_compatibility VARCHAR(20),
    sensor_name VARCHAR(120),
    max_dpi INTEGER,
    max_polling_rate_hz INTEGER,
    tracking_speed_ips INTEGER,
    acceleration_g NUMERIC(6,2),
    button_count INTEGER,
    side_button_count INTEGER,
    switch_name VARCHAR(120),
    encoder_name VARCHAR(120),
    connection_modes VARCHAR(120) NOT NULL DEFAULT 'wired',
    material VARCHAR(80),
    material_general VARCHAR(80),
    material_specific VARCHAR(120),
    hump_placement VARCHAR(30),
    front_flare VARCHAR(30),
    side_curvature VARCHAR(30),
    thumb_rest BOOLEAN,
    ring_finger_rest BOOLEAN,
    sensor_type VARCHAR(20),
    adjustable_sensor_position BOOLEAN,
    sensor_position_x NUMERIC(6,2),
    sensor_position_y NUMERIC(6,2),
    sensor_position_x2 NUMERIC(6,2),
    sensor_position_y2 NUMERIC(6,2),
    hot_swappable_switches BOOLEAN,
    switch_type VARCHAR(20),
    switch_life_span_m INTEGER,
    encoder_type VARCHAR(20),
    encoder_steps INTEGER,
    purchase_channels VARCHAR(500),
    image_url VARCHAR(600),
    primary_source_url VARCHAR(600),
    source_notes VARCHAR(1000),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mouse_identity UNIQUE (brand, model, variant)
);

ALTER TABLE mice ADD COLUMN IF NOT EXISTS material_general VARCHAR(80);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS material_specific VARCHAR(120);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS hump_placement VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS front_flare VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS side_curvature VARCHAR(30);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS thumb_rest BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS ring_finger_rest BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS adjustable_sensor_position BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_x NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_y NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_x2 NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS sensor_position_y2 NUMERIC(6,2);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS hot_swappable_switches BOOLEAN;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS switch_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS switch_life_span_m INTEGER;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS encoder_type VARCHAR(20);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS encoder_steps INTEGER;
ALTER TABLE mice ADD COLUMN IF NOT EXISTS purchase_channels VARCHAR(500);
ALTER TABLE mice ADD COLUMN IF NOT EXISTS image_url VARCHAR(600);

CREATE INDEX IF NOT EXISTS idx_mice_status_created ON mice(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mice_brand ON mice(brand);

CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    mouse_id UUID NOT NULL REFERENCES mice(id),
    grip_style VARCHAR(20) NOT NULL,
    hand_size VARCHAR(20) NOT NULL,
    usage_duration VARCHAR(30) NOT NULL,
    comfort_score INTEGER NOT NULL CHECK (comfort_score BETWEEN 1 AND 5),
    click_score INTEGER NOT NULL CHECK (click_score BETWEEN 1 AND 5),
    scroll_score INTEGER NOT NULL CHECK (scroll_score BETWEEN 1 AND 5),
    build_score INTEGER NOT NULL CHECK (build_score BETWEEN 1 AND 5),
    value_score INTEGER NOT NULL CHECK (value_score BETWEEN 1 AND 5),
    overall_score NUMERIC(2,1) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_review_user_mouse UNIQUE (user_id, mouse_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_mouse_active ON reviews(mouse_id, status, deleted_at);

CREATE TABLE IF NOT EXISTS review_pro_tags (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    tag_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (review_id, tag_code)
);

CREATE TABLE IF NOT EXISTS review_con_tags (
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    tag_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (review_id, tag_code)
);
