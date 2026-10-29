import hashlib
import importlib.util
import json
import pathlib
import unittest
from unittest.mock import MagicMock

spec = importlib.util.spec_from_file_location('transport', pathlib.Path(__file__).parents[1] / 'handler.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
ORIGIN = 'https://app.example.test'
USER = '11111111-1111-1111-1111-111111111111'
TOKEN = 'A' * 43
NOW = 1000

class Registry:
    def __init__(self): self.items = {}; self.pages = []
    def delete_item(self, Key, **kwargs):
        old = self.items.pop(Key['pk'], None)
        return {'Attributes': old} if old else {}
    def put_item(self, Item): self.items[Item['pk']] = Item
    def get_item(self, Key, **kwargs): return {'Item': self.items.get(Key['pk'], {})}
    def query(self, **kwargs): return self.pages.pop(0) if self.pages else {'Items': []}

class Gone(Exception): pass

class TransportTest(unittest.TestCase):
    def setUp(self):
        self.table = Registry(); self.api = MagicMock()
        self.api.exceptions.GoneException = Gone
        self.handler = module.create_handler(self.table, self.api, {ORIGIN}, lambda: NOW)
    def event(self, route='$connect', origin=ORIGIN, ticket=TOKEN):
        return {'requestContext': {'routeKey': route, 'connectionId': 'c1'}, 'headers': {'Origin': origin}, 'queryStringParameters': {'ticket': ticket}}
    def ticket(self, **overrides):
        value = {'userIdTicket': USER, 'expiresAt': NOW + 60, 'authExpiresAt': NOW + 3600, **overrides}
        self.table.items['TICKET#' + hashlib.sha256(TOKEN.encode()).hexdigest()] = value
    def record(self, identifier='m1', **overrides):
        return {'messageId': identifier, 'body': json.dumps({'version': 1, 'recipientId': USER, 'notificationId': '12', **overrides})}
    def connection(self, **overrides):
        return {'pk': 'CONNECTION#c1', 'connectionId': 'c1', 'userId': USER, 'expiresAt': NOW + 100, **overrides}
    def test_valid_ticket_is_bound_to_server_identity_and_consumed_once(self):
        self.ticket(); self.assertEqual(200, self.handler(self.event())['statusCode'])
        self.assertEqual(USER, self.table.items['CONNECTION#c1']['userId'])
        self.assertEqual(401, self.handler(self.event())['statusCode'])
    def test_foreign_origin_cannot_consume_ticket(self):
        self.ticket(); self.assertEqual(403, self.handler(self.event(origin='https://evil.test'))['statusCode'])
        self.assertEqual(200, self.handler(self.event())['statusCode'])
    def test_missing_or_malformed_ticket_rejected(self):
        for token in ['', 'x', 'Bearer secret']:
            self.assertEqual(401, self.handler(self.event(ticket=token))['statusCode'])
    def test_expired_ttl_not_accepted_even_before_dynamodb_deletes_it(self):
        self.ticket(expiresAt=NOW); self.assertEqual(401, self.handler(self.event())['statusCode'])
    def test_expired_authentication_rejected(self):
        self.ticket(authExpiresAt=NOW); self.assertEqual(401, self.handler(self.event())['statusCode'])
    def test_disconnect_removes_connection_and_is_idempotent(self):
        self.table.put_item(Item=self.connection())
        for _ in range(2): self.assertEqual(200, self.handler(self.event('$disconnect'))['statusCode'])
        self.assertEqual({}, self.table.items)
    def test_client_cannot_publish_or_choose_recipient(self):
        self.assertEqual(400, self.handler(self.event('publish'))['statusCode']); self.api.post_to_connection.assert_not_called()
    def test_heartbeat_requires_unexpired_connection(self):
        self.table.put_item(Item=self.connection()); self.handler(self.event('ping'))
        self.api.post_to_connection.assert_called_once_with(ConnectionId='c1', Data=b'{"type":"pong"}')
        self.table.put_item(Item=self.connection(expiresAt=NOW))
        self.assertEqual(401, self.handler(self.event('ping'))['statusCode']); self.api.delete_connection.assert_called_once()
    def test_fanout_uses_recipient_index_paginates_and_sends_only_invalidation(self):
        self.table.pages = [{'Items': [self.connection()], 'LastEvaluatedKey': {'pk': 'next'}}, {'Items': [self.connection(connectionId='c2')]}]
        self.assertEqual({'batchItemFailures': []}, self.handler({'Records': [self.record()]}))
        self.assertEqual(2, self.api.post_to_connection.call_count)
        message = json.loads(self.api.post_to_connection.call_args.kwargs['Data']); self.assertEqual({'type': 'notifications.changed', 'notificationId': '12'}, message)
    def test_stale_connection_410_is_cleaned_without_retrying_notice(self):
        self.table.put_item(Item=self.connection()); self.table.pages = [{'Items': [self.connection()]}]
        self.api.post_to_connection.side_effect = Gone()
        self.assertEqual([], self.handler({'Records': [self.record()]})['batchItemFailures']); self.assertEqual({}, self.table.items)
    def test_transient_failure_retries_only_failed_sqs_item(self):
        self.table.pages = [{'Items': [self.connection()]}, {'Items': []}]
        self.api.post_to_connection.side_effect = RuntimeError('temporary')
        self.assertEqual([{'itemIdentifier': 'm1'}], self.handler({'Records': [self.record(), self.record('m2')]})['batchItemFailures'])
    def test_malformed_message_returns_partial_failure(self):
        self.assertEqual([{'itemIdentifier': 'm1'}], self.handler({'Records': [self.record(recipientId='bad')]})['batchItemFailures'])
    def test_offline_recipient_completes_transport_without_discarding_database_notice(self):
        self.assertEqual([], self.handler({'Records': [self.record()]})['batchItemFailures']); self.api.post_to_connection.assert_not_called()
    def test_expired_connection_is_not_sent_a_notice(self):
        self.table.pages = [{'Items': [self.connection(expiresAt=NOW)]}]
        self.assertEqual([], self.handler({'Records': [self.record()]})['batchItemFailures']); self.api.post_to_connection.assert_not_called()
        self.api.delete_connection.assert_called_once()

if __name__ == '__main__': unittest.main()
