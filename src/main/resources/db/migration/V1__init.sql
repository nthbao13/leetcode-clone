CREATE TABLE problems (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255),
    description TEXT,
    test_cases  JSONB
);

CREATE TABLE problem_detail (
    id            BIGSERIAL PRIMARY KEY,
    problem_id    BIGINT REFERENCES problems (id),
    language      VARCHAR(50),
    code_template TEXT
);

CREATE TABLE submissions (
    id            BIGSERIAL PRIMARY KEY,
    problem_id    BIGINT REFERENCES problems (id),
    language      VARCHAR(50),
    code_submit   TEXT,
    build_status  VARCHAR(50),
    submit_status VARCHAR(50),
    output        TEXT,
    error_output  TEXT,
    runtime_ms    BIGINT
);
