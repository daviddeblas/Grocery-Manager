/*
 * Stores geographical information about retail stores.
 * Used for proximity alerts when user is near a saved store location.
 *
 * Features:
 * - Geographic coordinates for geofencing
 * - Unique geofence_id for location tracking
 * - Address and name for user display
 * - Cross-device synchronization support
 */
CREATE TABLE store_locations (
     id BIGSERIAL PRIMARY KEY,
     name VARCHAR(255) NOT NULL,          -- Store name for display
     address VARCHAR(500) NOT NULL,       -- Physical address of the store
     latitude DOUBLE PRECISION NOT NULL,  -- Geographic coordinate
     longitude DOUBLE PRECISION NOT NULL, -- Geographic coordinate
     geofence_id VARCHAR(100) NOT NULL,   -- ID for geofencing API (mobile device)
     user_id BIGINT NOT NULL,             -- Owner of this store location
     sync_id VARCHAR(100) UNIQUE,         -- UUID for synchronization
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     last_synced TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     version BIGINT DEFAULT 0,            -- For optimistic locking
     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for geofencing and synchronization
CREATE INDEX idx_store_locations_user_id ON store_locations(user_id);
CREATE INDEX idx_store_locations_geofence_id ON store_locations(geofence_id);
CREATE INDEX idx_store_locations_sync_id ON store_locations(sync_id);