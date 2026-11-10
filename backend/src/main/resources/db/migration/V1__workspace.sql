-- Preserve legacy demo records and any locally edited values.
CREATE TABLE IF NOT EXISTS demo_order(order_id text primary key,channel text not null,order_value numeric(14,2) not null,payment text not null,delivery text not null,expected numeric(14,2) not null,received numeric(14,2) not null,due_date date not null);
INSERT INTO demo_order VALUES
('ORD-W-204118','Website',1499,'COD','Delivered',1409,0,'2026-10-25'),
('ORD-M-88120','Marketplace 1',1199,'Prepaid','Delivered',899,862,'2026-10-25'),
('ORD-W-204377','Website',2398,'Prepaid','Returned to warehouse',-96,-142,'2026-10-25') ON CONFLICT DO NOTHING;
CREATE TABLE user_account(username text PRIMARY KEY,password_hash text NOT NULL,role text NOT NULL CHECK(role IN ('FINANCE','FOUNDER')));
CREATE TABLE mapping_version(id bigserial PRIMARY KEY,partner text NOT NULL,report_type text NOT NULL,mapping jsonb NOT NULL,approved_by text NOT NULL,predecessor bigint REFERENCES mapping_version(id),created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX mapping_partner ON mapping_version(partner,report_type,id DESC);
CREATE TABLE import_batch(id uuid PRIMARY KEY,partner text NOT NULL,report_type text NOT NULL,filename text NOT NULL,sha256 text NOT NULL,storage_path text NOT NULL,state text NOT NULL,headers jsonb NOT NULL DEFAULT '[]',row_count integer NOT NULL DEFAULT 0,processed integer NOT NULL DEFAULT 0,accepted integer NOT NULL DEFAULT 0,error_count integer NOT NULL DEFAULT 0,error_message text,mapping_id bigint REFERENCES mapping_version(id),created_by text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),completed_at timestamptz,UNIQUE(partner,report_type,sha256));
CREATE TABLE source_row(import_id uuid REFERENCES import_batch(id),row_number integer NOT NULL,raw_data jsonb NOT NULL,canonical jsonb,PRIMARY KEY(import_id,row_number));
CREATE TABLE row_error(id bigserial PRIMARY KEY,import_id uuid REFERENCES import_batch(id),row_number integer NOT NULL,message text NOT NULL);
CREATE INDEX row_error_import ON row_error(import_id,row_number);
CREATE TABLE rate_card(id bigserial PRIMARY KEY,partner text NOT NULL,kind text NOT NULL CHECK(kind IN ('CHANNEL','COURIER')),effective_from date NOT NULL,effective_to date NOT NULL,commission numeric(7,4) NOT NULL CHECK(commission BETWEEN 0 AND 100),fixed_fee numeric(14,2) NOT NULL CHECK(fixed_fee>=0),return_fee numeric(14,2) NOT NULL CHECK(return_fee>=0),settlement_days integer NOT NULL CHECK(settlement_days BETWEEN 0 AND 365),created_by text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),CHECK(effective_to>=effective_from));
CREATE INDEX rate_card_dates ON rate_card(partner,kind,effective_from,effective_to);
CREATE TABLE order_record(id bigserial PRIMARY KEY,partner text NOT NULL,external_id text NOT NULL,value numeric(14,2) NOT NULL CHECK(value>=0),payment text NOT NULL,delivery text NOT NULL,order_date date NOT NULL,due_date date,return_due date,return_received boolean NOT NULL DEFAULT false,expected_override numeric(14,2),import_id uuid REFERENCES import_batch(id),row_number integer,UNIQUE(partner,external_id));
CREATE TABLE movement(id bigserial PRIMARY KEY,partner text NOT NULL,external_id text,transaction_id text NOT NULL,kind text NOT NULL CHECK(kind IN ('SETTLEMENTS','COD','FREIGHT','RETURNS')),amount numeric(14,2) NOT NULL,tcs numeric(14,2) NOT NULL DEFAULT 0 CHECK(tcs>=0),tds numeric(14,2) NOT NULL DEFAULT 0 CHECK(tds>=0),occurred_on date NOT NULL,import_id uuid REFERENCES import_batch(id),row_number integer,UNIQUE(partner,transaction_id));
CREATE INDEX movement_order ON movement(partner,external_id);
CREATE TABLE reconciliation_entry(order_id bigint PRIMARY KEY REFERENCES order_record(id),expected numeric(14,2),received numeric(14,2) NOT NULL,difference numeric(14,2),money_at_risk numeric(14,2) NOT NULL,status text NOT NULL,tcs numeric(14,2) NOT NULL,tds numeric(14,2) NOT NULL,breakdown jsonb NOT NULL,as_of date NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX reconciliation_status ON reconciliation_entry(status,order_id);
CREATE TABLE exception(id bigserial PRIMARY KEY,order_id bigint REFERENCES order_record(id),type text NOT NULL,amount numeric(14,2) NOT NULL CHECK(amount>=0),active boolean NOT NULL DEFAULT true,opened_on date NOT NULL,UNIQUE(order_id,type));
CREATE INDEX exception_active ON exception(active,order_id);
CREATE TABLE claim(id bigserial PRIMARY KEY,exception_id bigint NOT NULL REFERENCES exception(id),status text NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED')),note text NOT NULL,snapshot jsonb NOT NULL,created_by text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX one_active_claim ON claim(exception_id) WHERE status IN ('OPEN','IN_REVIEW');
CREATE TABLE audit_event(id bigserial PRIMARY KEY,actor text NOT NULL,action text NOT NULL,entity_type text NOT NULL,entity_id text NOT NULL,details jsonb NOT NULL DEFAULT '{}',created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX audit_entity ON audit_event(entity_type,entity_id,id);
INSERT INTO rate_card(partner,kind,effective_from,effective_to,commission,fixed_fee,return_fee,settlement_days,created_by) VALUES
('Website','CHANNEL','2020-01-01','2099-12-31',0,90,96,7,'seed'),
('Marketplace 1','CHANNEL','2020-01-01','2099-12-31',20,60.20,96,14,'seed'),
('Website','COURIER','2020-01-01','2099-12-31',0,70,96,7,'seed'),
('Marketplace 1','COURIER','2020-01-01','2099-12-31',0,70,96,7,'seed');
INSERT INTO order_record(partner,external_id,value,payment,delivery,order_date,due_date,return_received,expected_override)
SELECT channel,order_id,order_value,payment,delivery,'2026-10-18',due_date,delivery='Returned to warehouse',expected FROM demo_order;
INSERT INTO movement(partner,external_id,transaction_id,kind,amount,occurred_on)
SELECT channel,order_id,'SEED-'||order_id,'SETTLEMENTS',received,'2026-10-25' FROM demo_order WHERE received<>0;
