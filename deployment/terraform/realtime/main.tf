variable "aws_region" { type = string }
variable "project" { type = string }
variable "environment" { type = string }
variable "backend_task_role_name" { type = string }
variable "allowed_origins" {
  type = list(string)
  validation {
    condition     = length(var.allowed_origins) > 0 && alltrue([for o in var.allowed_origins : startswith(o, "https://") && !strcontains(o, "*")])
    error_message = "Use explicit HTTPS browser origins."
  }
}
provider "aws" { region = var.aws_region }
data "aws_caller_identity" "current" {}
locals { name = "${var.project}-${var.environment}-notifications" }
resource "aws_dynamodb_table" "connections" {
  name         = local.name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "userId"
    type = "S"
  }
  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
  server_side_encryption { enabled = true }
  point_in_time_recovery { enabled = true }
  tags = { project = var.project, environment = var.environment, managed-by = "terraform" }
}
resource "aws_sqs_queue" "dead" {
  name                      = "${local.name}-dead"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}
resource "aws_sqs_queue" "events" {
  name                       = local.name
  visibility_timeout_seconds = 180
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true
  redrive_policy             = jsonencode({ deadLetterTargetArn = aws_sqs_queue.dead.arn, maxReceiveCount = 5 })
}
resource "aws_apigatewayv2_api" "websocket" {
  name                       = local.name
  protocol_type              = "WEBSOCKET"
  route_selection_expression = "$request.body.action"
}
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.name}"
  retention_in_days = 14
}
resource "aws_cloudwatch_log_group" "gateway" {
  name              = "/aws/apigateway/${local.name}"
  retention_in_days = 14
}
resource "aws_iam_role" "lambda" {
  name               = local.name
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}
resource "aws_iam_role_policy" "lambda" {
  name = local.name
  role = aws_iam_role.lambda.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.lambda.arn}:*" },
    { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem", "dynamodb:Query"], Resource = [aws_dynamodb_table.connections.arn, "${aws_dynamodb_table.connections.arn}/index/userId-index"] },
    { Effect = "Allow", Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"], Resource = aws_sqs_queue.events.arn },
    { Effect = "Allow", Action = ["execute-api:ManageConnections"], Resource = "${aws_apigatewayv2_api.websocket.execution_arn}/qa/POST/@connections/*" },
    { Effect = "Allow", Action = ["execute-api:ManageConnections"], Resource = "${aws_apigatewayv2_api.websocket.execution_arn}/qa/DELETE/@connections/*" }
  ] })
}
# ECS can mint short-lived tickets and publish envelopes, not query other users' connections.
resource "aws_iam_role_policy" "backend" {
  name = local.name
  role = var.backend_task_role_name
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["dynamodb:PutItem"], Resource = aws_dynamodb_table.connections.arn, Condition = { "ForAllValues:StringLike" = { "dynamodb:LeadingKeys" = ["TICKET#*"] } } },
    { Effect = "Allow", Action = ["sqs:SendMessage"], Resource = aws_sqs_queue.events.arn }
  ] })
}
data "archive_file" "lambda" {
  type        = "zip"
  source_file = "${path.module}/../../realtime/handler.py"
  output_path = "${path.module}/lambda.zip"
}
resource "aws_lambda_function" "notifications" {
  function_name    = local.name
  role             = aws_iam_role.lambda.arn
  runtime          = "python3.13"
  handler          = "handler.handler"
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  timeout          = 30
  memory_size      = 256
  # This QA account has only10 unreserved executions; reserving any is rejected.
  reserved_concurrent_executions = -1
  environment { variables = {
    CONNECTIONS_TABLE = aws_dynamodb_table.connections.name
    ALLOWED_ORIGINS   = jsonencode(var.allowed_origins)
    MANAGEMENT_URL    = "https://${aws_apigatewayv2_api.websocket.id}.execute-api.${var.aws_region}.amazonaws.com/qa"
  } }
  depends_on = [aws_cloudwatch_log_group.lambda, aws_iam_role_policy.lambda]
}
resource "aws_lambda_permission" "gateway" {
  statement_id  = "AllowWebSocketGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notifications.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.websocket.execution_arn}/*"
}
resource "aws_apigatewayv2_integration" "lambda" {
  api_id             = aws_apigatewayv2_api.websocket.id
  integration_type   = "AWS_PROXY"
  integration_method = "POST"
  integration_uri    = aws_lambda_function.notifications.invoke_arn
}
resource "aws_apigatewayv2_route" "routes" {
  for_each  = toset(["$connect", "$disconnect", "$default", "ping"])
  api_id    = aws_apigatewayv2_api.websocket.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  # One-use tickets are consumed by connect integration; no anonymous successful connections.
  authorization_type = "NONE"
}
resource "aws_apigatewayv2_stage" "qa" {
  depends_on  = [aws_api_gateway_account.logging]
  api_id      = aws_apigatewayv2_api.websocket.id
  name        = "qa"
  auto_deploy = true
  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.gateway.arn
    # Do not log query strings, tickets, headers or message bodies.
    format = jsonencode({ requestId = "$context.requestId", routeKey = "$context.routeKey", status = "$context.status", connectionId = "$context.connectionId" })
  }
}
resource "aws_lambda_event_source_mapping" "events" {
  event_source_arn        = aws_sqs_queue.events.arn
  function_name           = aws_lambda_function.notifications.arn
  batch_size              = 10
  function_response_types = ["ReportBatchItemFailures"]
  scaling_config { maximum_concurrency = 5 }
}
resource "aws_cloudwatch_metric_alarm" "dead_letters" {
  alarm_name          = "${local.name}-dead-letters"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dead.name }
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  evaluation_periods  = 1
  period              = 60
  statistic           = "Maximum"
  treat_missing_data  = "notBreaching"
}
output "websocket_url" { value = "${aws_apigatewayv2_api.websocket.api_endpoint}/qa" }
output "connections_table" { value = aws_dynamodb_table.connections.name }
output "queue_url" { value = aws_sqs_queue.events.url }
output "dead_letter_queue_url" { value = aws_sqs_queue.dead.url }
output "function_name" { value = aws_lambda_function.notifications.function_name }

# WebSocket access logging needs a regional account role, unlike the HTTP API.
# Verified unset before adopting this shared setting. Do not overwrite a role
# managed by another stack: import/reuse that ownership instead.
resource "aws_iam_role" "gateway_logs" {
  name               = "${local.name}-gateway-logs"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "apigateway.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  lifecycle { prevent_destroy = true }
}
resource "aws_iam_role_policy" "gateway_logs" {
  name = "${local.name}-gateway-logs"
  role = aws_iam_role.gateway_logs.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    # Account-level API Gateway verification needs the standard seven CloudWatch
    # permissions, including creating its validation log group. This shared role
    # has no application, database, Cognito or IAM administration permissions.
    { Effect = "Allow", Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:DescribeLogGroups", "logs:DescribeLogStreams", "logs:PutLogEvents", "logs:GetLogEvents", "logs:FilterLogEvents"], Resource = "*" }
  ] })
}
resource "aws_api_gateway_account" "logging" {
  cloudwatch_role_arn = aws_iam_role.gateway_logs.arn
  depends_on          = [aws_iam_role_policy.gateway_logs]
  lifecycle { prevent_destroy = true }
}
