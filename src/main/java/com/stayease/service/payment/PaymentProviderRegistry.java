package com.stayease.service.payment;

import com.stayease.exception.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry holding all available {@link PaymentProvider} implementations.
 * Selects the active provider according to application configuration.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    @Value("${app.payment.provider:sandbox}")
    private String activeProviderName;

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toUpperCase(),
                        p -> p
                ));
    }

    /**
     * Retrieves the default configured active provider.
     */
    public PaymentProvider getActiveProvider() {
        return getProvider(activeProviderName);
    }

    /**
     * Retrieves a provider by name (case-insensitive).
     */
    public PaymentProvider getProvider(String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) {
            providerName = "SANDBOX";
        }
        PaymentProvider provider = providers.get(providerName.trim().toUpperCase());
        if (provider == null) {
            throw new PaymentGatewayException("Unsupported payment provider: " + providerName);
        }
        return provider;
    }
}
