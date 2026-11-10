CREATE TABLE match_suggestion(
 id uuid PRIMARY KEY,
 movement_id bigint NOT NULL REFERENCES movement(id),
 order_id bigint NOT NULL REFERENCES order_record(id),
 confidence numeric(5,4) NOT NULL CHECK(confidence BETWEEN 0 AND 1),
 reason text NOT NULL,
 state text NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','APPROVED','SUPERSEDED')),
 provider text NOT NULL,
 model text NOT NULL,
 created_by text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX suggestion_movement ON match_suggestion(movement_id,state,created_at DESC);
CREATE INDEX reconciliation_as_of ON reconciliation_entry(as_of);
