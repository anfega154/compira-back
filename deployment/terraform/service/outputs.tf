output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "api_gateway_url" {
  value = aws_apigatewayv2_api.this.api_endpoint
}

output "api_gateway_stage_name" {
  value = aws_apigatewayv2_stage.default.name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "nat_public_ip" {
  value = aws_eip.nat.public_ip
}

output "aurora_endpoint" { value = aws_rds_cluster.aurora.endpoint }
output "frontend_api_url" { value = aws_apigatewayv2_api.this.api_endpoint }
