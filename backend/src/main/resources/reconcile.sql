WITH selected_orders AS (
 SELECT o.* FROM order_record o WHERE /*ORDER_SCOPE*/
), ledger AS (
 SELECT m.partner,m.external_id,
 coalesce(sum(amount) FILTER(WHERE kind IN ('SETTLEMENTS','COD')),0) received,
 coalesce(sum(tcs) FILTER(WHERE kind IN ('SETTLEMENTS','COD')),0) tcs,
 coalesce(sum(tds) FILTER(WHERE kind IN ('SETTLEMENTS','COD')),0) tds,
 coalesce(sum(amount) FILTER(WHERE kind='FREIGHT'),0) freight,
 count(*) FILTER(WHERE kind='FREIGHT') freight_count,
 bool_or(kind='RETURNS') warehouse_received
 FROM movement m JOIN selected_orders chosen ON chosen.partner=m.partner AND chosen.external_id=m.external_id
 WHERE occurred_on<=CAST(:asOf AS date) GROUP BY m.partner,m.external_id
), calculated AS (
 SELECT o.*,coalesce(l.received,0) received,coalesce(l.tcs,0) tcs,coalesce(l.tds,0) tds,
 c.id channel_version,f.id courier_version,c.commission,c.fixed_fee,c.return_fee,
 round(o.value*coalesce(c.commission,0)/100,2) commission_fee,
 coalesce(o.due_date,o.order_date+c.settlement_days) effective_due,
 coalesce(o.expected_override,CASE WHEN o.delivery IN ('RETURN_EXPECTED','RETURNED','Returned to warehouse') THEN -c.return_fee-coalesce(l.tcs,0)-coalesce(l.tds,0)
 ELSE o.value-round(o.value*c.commission/100,2)-c.fixed_fee-coalesce(l.tcs,0)-coalesce(l.tds,0) END) expected,
 CASE WHEN coalesce(l.freight_count,0)>0 AND f.id IS NOT NULL
 THEN greatest(0,l.freight-CASE WHEN o.delivery IN ('RETURN_EXPECTED','RETURNED','Returned to warehouse') THEN f.return_fee ELSE f.fixed_fee END) ELSE 0 END freight_gap,
 coalesce(l.freight,0) freight_charged,
 CASE WHEN o.delivery='RETURN_EXPECTED' AND o.value>10 AND NOT(o.return_received OR coalesce(l.warehouse_received,false))
 AND CAST(:asOf AS date)>coalesce(o.return_due,o.order_date+CAST(:returnDays AS integer)) THEN o.value ELSE 0 END return_risk,
 (c.id IS NULL AND o.expected_override IS NULL) OR (coalesce(l.freight_count,0)>0 AND f.id IS NULL) manual
 FROM selected_orders o LEFT JOIN ledger l ON l.partner=o.partner AND l.external_id=o.external_id
 LEFT JOIN rate_card c ON c.partner=o.partner AND c.kind='CHANNEL' AND o.order_date BETWEEN c.effective_from AND c.effective_to
 LEFT JOIN rate_card f ON f.partner=o.partner AND f.kind='COURIER' AND o.order_date BETWEEN f.effective_from AND f.effective_to
), classified AS (
 SELECT *,received-expected difference,
 CASE WHEN manual THEN 'MANUAL_REVIEW'
 WHEN abs(received-expected)<=10 THEN 'SETTLED'
 WHEN received-expected>10 THEN 'OVERPAID'
 WHEN CAST(:asOf AS date)<=effective_due THEN 'PENDING'
 WHEN received=0 AND expected>0 THEN 'UNPAID_PAST_DUE' ELSE 'SHORT_PAID' END financial_status
 FROM calculated
), risks AS (
 SELECT *,CASE WHEN financial_status IN ('SHORT_PAID','UNPAID_PAST_DUE') THEN expected-received ELSE 0 END financial_risk,
 CASE WHEN freight_gap>10 THEN freight_gap ELSE 0 END freight_risk FROM classified
)
INSERT INTO reconciliation_entry(order_id,expected,received,difference,money_at_risk,status,tcs,tds,breakdown,as_of)
SELECT id,expected,received,difference,financial_risk+freight_risk+return_risk,
 CASE WHEN manual THEN 'MANUAL_REVIEW' WHEN return_risk>0 THEN 'RETURN_NOT_RECEIVED'
 WHEN freight_risk>0 THEN 'FREIGHT_OVERCHARGED' ELSE financial_status END,
 tcs,tds,jsonb_build_object('channelVersion',channel_version,'courierVersion',courier_version,
 'commissionPercent',commission,'commissionFee',commission_fee,'fixedFee',fixed_fee,'returnFee',return_fee,
 'expectedSource',CASE WHEN expected_override IS NULL THEN 'RATE_CARD' ELSE 'BRIEF_SEED_OVERRIDE' END,
 'dueDate',effective_due,'returnDue',coalesce(return_due,order_date+CAST(:returnDays AS integer)),
 'financialStatus',financial_status,'financialRisk',financial_risk,'freightRisk',freight_risk,
 'returnRisk',return_risk,'freightCharged',freight_charged,'taxesExcludedFromLeakage',true),CAST(:asOf AS date)
FROM risks
ON CONFLICT(order_id) DO UPDATE SET expected=excluded.expected,received=excluded.received,difference=excluded.difference,
 money_at_risk=excluded.money_at_risk,status=excluded.status,tcs=excluded.tcs,tds=excluded.tds,breakdown=excluded.breakdown,as_of=excluded.as_of,updated_at=now()
