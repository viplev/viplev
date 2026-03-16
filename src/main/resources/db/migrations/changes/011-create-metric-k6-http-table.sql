CREATE TABLE metric_k6_http (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES benchmark_runs(id),
    collected_at TIMESTAMP NOT NULL,
    url VARCHAR(2048),
    http_method VARCHAR(10) NOT NULL,
    request_group VARCHAR(255),
    http_status INT,
    expected_status INT,
    data_received_byte INT,
    data_sent_byte INT,
    http_req_duration_ms INT,
    http_req_waiting_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_metric_k6_http_run_id ON metric_k6_http(run_id);
