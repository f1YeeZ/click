CREATE TABLE page_view_events (
    id UUID PRIMARY KEY,
    visitor_hash VARCHAR(64) NOT NULL,
    path VARCHAR(240) NOT NULL,
    view_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_page_view_events_date ON page_view_events(view_date);
CREATE INDEX idx_page_view_events_date_visitor ON page_view_events(view_date, visitor_hash);
CREATE INDEX idx_page_view_events_path_date ON page_view_events(path, view_date);
