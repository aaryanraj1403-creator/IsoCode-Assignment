CREATE TABLE IF NOT EXISTS demo_order(order_id text primary key,channel text not null,order_value numeric(14,2) not null,payment text not null,delivery text not null,expected numeric(14,2) not null,received numeric(14,2) not null,due_date date not null);
INSERT INTO demo_order VALUES
('ORD-W-204118','Website',1499,'COD','Delivered',1409,0,'2026-10-25'),
('ORD-M-88120','Marketplace 1',1199,'Prepaid','Delivered',899,862,'2026-10-25'),
('ORD-W-204377','Website',2398,'Prepaid','Returned to warehouse',-96,-142,'2026-10-25') ON CONFLICT DO NOTHING;
