"""Opt-in live-provider smoke test through Reconflow's authenticated HTTP API.

Makes two provider requests when successful. Retains uniquely named synthetic
reports/rate cards/orders as evidence. Uses the backend's provider configuration;
never prints the API key or includes it in verification evidence.
"""
import argparse
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import json
import time
import urllib.parse
import uuid

from benchmark import Client, ROOT, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:5173')
    parser.add_argument('--out', default='.local/live-ai-verification.json')
    args = parser.parse_args()
    result = {'checked_at': datetime.now(timezone(timedelta(hours=5, minutes=30))).isoformat(),
              'base_url': args.base, 'synthetic_data_only': True, 'checks': {}}
    output = ROOT / args.out
    output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        output.write_text(json.dumps(result, indent=2), encoding='utf-8')

    def check(name, condition):
        result['checks'][name] = bool(condition)
        save()
        if not condition:
            raise AssertionError(name)

    try:
        env = environment()
        finance = Client(args.base)
        finance.login('finance', env['DEMO_FINANCE_PASSWORD'])
        ai = finance.request('/ai/status')
        result['provider'] = ai['provider']
        result['model'] = ai['model']
        check('backend_configured', ai['configured'])
        prefix = 'LIVE-AI-' + uuid.uuid4().hex[:10]
        result['partner'] = prefix
        folder = ROOT / '.local' / prefix
        folder.mkdir(parents=True)
        founder = Client(args.base)
        founder.login('founder', env['DEMO_FOUNDER_PASSWORD'])
        founder.request('/rate-cards', {
            'partner': prefix, 'kind': 'CHANNEL', 'effectiveFrom': '2020-01-01',
            'effectiveTo': '2099-12-31', 'commission': 0, 'fixedFee': 90,
            'returnFee': 96, 'settlementDays': 7})

        path = folder / 'orders.csv'
        path.write_text('Order Number,Order Amount,Payment Method,Delivery Status,Order Date\n'
                        + prefix + '-ORDER,1000,COD,DELIVERED,2026-01-01\n', encoding='utf-8')
        imported = finance.upload(path, prefix, 'ORDERS')
        result['order_import_id'] = imported['id']
        finance.wait(imported['id'], 'AWAITING_MAPPING')
        start = time.perf_counter()
        mapping_ai = finance.request('/imports/' + imported['id'] + '/suggest-mapping', {})
        result['mapping_seconds'] = round(time.perf_counter() - start, 3)
        result['mapping_response'] = mapping_ai
        check('live_mapping_available', mapping_ai['available'])
        check('mapping_requires_approval', mapping_ai['requiresApproval'])
        check('suggesting_did_not_process_import', finance.request('/imports/' + imported['id'])['state'] == 'AWAITING_MAPPING')
        mapping = {r['field']: r['header'] for r in mapping_ai['suggestions'] if r['header'] is not None}
        expected = {'order_id': 'Order Number', 'value': 'Order Amount', 'payment': 'Payment Method',
                    'delivery': 'Delivery Status', 'order_date': 'Order Date'}
        check('required_mapping_correct', all(mapping.get(k) == v for k, v in expected.items()))
        preview = finance.request('/imports/' + imported['id'] + '/preview', {'mapping': mapping})
        check('preview_valid', len(preview) == 1 and not any('error' in row for row in preview))
        finance.request('/imports/' + imported['id'] + '/approve', {'mapping': mapping, 'approve': True})
        completed = finance.wait(imported['id'], 'COMPLETE')
        check('order_import_accepted', completed['accepted'] == 1 and completed['error_count'] == 0)

        path = folder / 'cod.csv'
        path.write_text('transaction_id,order_id,amount,date\n' + prefix + '-TX,,910,2026-01-08\n', encoding='utf-8')
        receipt = finance.upload(path, prefix, 'COD')
        result['receipt_import_id'] = receipt['id']
        finance.wait(receipt['id'], 'AWAITING_MAPPING')
        cod_mapping = {x: x for x in ('transaction_id', 'order_id', 'amount', 'date')}
        finance.request('/imports/' + receipt['id'] + '/preview', {'mapping': cod_mapping})
        finance.request('/imports/' + receipt['id'] + '/approve', {'mapping': cod_mapping, 'approve': True})
        finance.wait(receipt['id'], 'COMPLETE')
        query = '?' + urllib.parse.urlencode({'partner': prefix})
        order = finance.request('/orders' + query)['rows'][0]
        unmatched = finance.request('/unmatched' + query)
        check('receipt_waits_for_matching', unmatched['total'] == 1 and Decimal(str(order['received'])) == 0)
        movement_id = unmatched['rows'][0]['id']
        result['movement_id'] = movement_id
        start = time.perf_counter()
        matching_ai = finance.request('/unmatched/' + str(movement_id) + '/suggest-match', {})
        result['matching_seconds'] = round(time.perf_counter() - start, 3)
        result['matching_response'] = matching_ai
        check('live_matching_available', matching_ai['available'])
        check('match_requires_approval', matching_ai['requiresApproval'])
        proposals = [p for p in matching_ai['suggestions'] if p['orderId'] == order['id']]
        check('correct_order_proposed', len(proposals) == 1)
        check('suggesting_did_not_link_receipt', finance.request('/unmatched' + query)['total'] == 1
              and Decimal(str(finance.request('/orders' + query)['rows'][0]['received'])) == 0)
        proposal = proposals[0]
        finance.request('/unmatched/' + str(movement_id) + '/match',
                        {'orderId': order['id'], 'suggestionId': proposal['id']})
        final = finance.request('/orders' + query)['rows'][0]
        check('explicit_approval_settled_order', final['status'] == 'SETTLED'
              and Decimal(str(final['received'])) == Decimal('910')
              and Decimal(str(final['money_at_risk'])) == 0)
        check('approved_receipt_no_longer_unmatched', finance.request('/unmatched' + query)['total'] == 0)
        result['status'] = 'PASS'
    except Exception as exc:
        result['status'] = 'FAIL'
        # Avoid writing raw transport exceptions or environment values to evidence.
        result['failure_type'] = type(exc).__name__
        result['failed_checks'] = [k for k, v in result['checks'].items() if not v]
    save()
    print(json.dumps({'status': result['status'], 'model': result.get('model'),
                      'checks': result['checks'], 'evidence': str(output)}, indent=2))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
