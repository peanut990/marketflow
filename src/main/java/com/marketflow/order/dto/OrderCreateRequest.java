package com.marketflow.order.dto;

import java.util.List;

public record OrderCreateRequest(
        List<Long> cartItemIds
) {
}
