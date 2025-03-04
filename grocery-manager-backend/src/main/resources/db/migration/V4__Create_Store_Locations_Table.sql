CREATE TABLE store_locations (
                                 id BIGSERIAL PRIMARY KEY,
                                 name VARCHAR(255) NOT NULL,
                                 address VARCHAR(500) NOT NULL,
                                 latitude DOUBLE PRECISION NOT NULL,
                                 longitude DOUBLE PRECISION NOT NULL,
                                 geofence_id VARCHAR(100) NOT NULL,
                                 user_id BIGINT NOT NULL,
                                 sync_id VARCHAR(100) UNIQUE,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 last_synced TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 version BIGINT DEFAULT 0,
                                 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_store_locations_user_id ON store_locations(user_id);
CREATE INDEX idx_store_locations_geofence_id ON store_locations(geofence_id);
CREATE INDEX idx_store_locations_sync_id ON store_locations(sync_id);