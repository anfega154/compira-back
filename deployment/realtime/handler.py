"""Managed notification transport. No Cognito bearer tokens or notification content are logged."""
import hashlib
import json
import os
import re
import time


def create_handler(table, management, allowed_origins, clock=time.time):
    def remove(connection):
        table.delete_item(Key={"pk": "CONNECTION#" + connection})

    def close(connection):
        try:
            management.delete_connection(ConnectionId=connection)
        except management.exceptions.GoneException:
            pass
        remove(connection)

    def send(recipient, notification_id):
        last = None
        while True:
            query = {"IndexName": "userId-index", "KeyConditionExpression": "userId = :user", "ExpressionAttributeValues": {":user": recipient}}
            if last:
                query["ExclusiveStartKey"] = last
            page = table.query(**query)
            for item in page.get("Items", []):
                connection = item["connectionId"]
                if int(item["expiresAt"]) <= int(clock()):
                    close(connection)
                    continue
                try:
                    management.post_to_connection(ConnectionId=connection, Data=json.dumps({"type": "notifications.changed", "notificationId": notification_id}).encode())
                except management.exceptions.GoneException:
                    remove(connection)
            last = page.get("LastEvaluatedKey")
            if not last:
                break

    def handler(event, context=None):
        if "Records" in event:
            failed = []
            for record in event["Records"]:
                try:
                    message = json.loads(record["body"])
                    if message.get("version") != 1 or not re.fullmatch(r"[0-9a-fA-F-]{36}", message.get("recipientId", "")) or not re.fullmatch(r"[1-9][0-9]*", str(message.get("notificationId", ""))):
                        raise ValueError("Invalid notification envelope")
                    send(message["recipientId"], str(message["notificationId"]))
                except Exception:
                    # Returning an item failure is what makes SQS retry, not a successful invocation.
                    print(json.dumps({"event": "notification_delivery_failed", "messageId": record["messageId"]}))
                    failed.append({"itemIdentifier": record["messageId"]})
            return {"batchItemFailures": failed}

        request = event["requestContext"]
        route, connection = request["routeKey"], request["connectionId"]
        if route == "$connect":
            headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
            if headers.get("origin") not in allowed_origins:
                return {"statusCode": 403}
            ticket = (event.get("queryStringParameters") or {}).get("ticket", "")
            if not re.fullmatch(r"[A-Za-z0-9_-]{43}", ticket):
                return {"statusCode": 401}
            key = "TICKET#" + hashlib.sha256(ticket.encode("ascii")).hexdigest()
            # Atomic consume: a concurrent replay cannot claim the same identity.
            saved = table.delete_item(Key={"pk": key}, ReturnValues="ALL_OLD").get("Attributes", {})
            now = int(clock())
            if int(saved.get("expiresAt", 0)) <= now or int(saved.get("authExpiresAt", 0)) <= now or not saved.get("userIdTicket"):
                return {"statusCode": 401}
            table.put_item(Item={"pk": "CONNECTION#" + connection, "connectionId": connection, "userId": saved["userIdTicket"], "expiresAt": min(int(saved["authExpiresAt"]), now + 7200)})
            return {"statusCode": 200}
        if route == "$disconnect":
            remove(connection)
            return {"statusCode": 200}
        # Only heartbeat is supported. Clients cannot supply a recipient or publish events.
        if route != "ping":
            return {"statusCode": 400}
        saved = table.get_item(Key={"pk": "CONNECTION#" + connection}, ConsistentRead=True).get("Item", {})
        if int(saved.get("expiresAt", 0)) <= int(clock()):
            close(connection)
            return {"statusCode": 401}
        try:
            management.post_to_connection(ConnectionId=connection, Data=b'{"type":"pong"}')
        except management.exceptions.GoneException:
            remove(connection)
        return {"statusCode": 200}
    return handler


_instance = None

def handler(event, context):
    global _instance
    if _instance is None:
        import boto3
        table = boto3.resource("dynamodb").Table(os.environ["CONNECTIONS_TABLE"])
        management = boto3.client("apigatewaymanagementapi", endpoint_url=os.environ["MANAGEMENT_URL"])
        _instance = create_handler(table, management, set(json.loads(os.environ["ALLOWED_ORIGINS"])))
    return _instance(event, context)
