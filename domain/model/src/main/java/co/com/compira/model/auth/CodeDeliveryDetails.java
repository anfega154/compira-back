package co.com.compira.model.auth;

public record CodeDeliveryDetails(
        String destination,
        String deliveryMedium,
        String attributeName) {
}
