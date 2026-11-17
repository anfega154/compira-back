output "bucket_name" { value = aws_s3_bucket.site.bucket }
output "cloudfront_distribution_id" { value = aws_cloudfront_distribution.site.id }
output "frontend_url" { value = "https://${aws_cloudfront_distribution.site.domain_name}" }
