package com.ilmiogarage.dto.email;

import java.util.List;

public record BrevoEmailPayload(
        BrevoSender sender,
        List<BrevoRecipient> to,
        String subject,
        String htmlContent,
        String textContent
) {
}
