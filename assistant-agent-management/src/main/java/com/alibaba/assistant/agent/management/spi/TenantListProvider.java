package com.alibaba.assistant.agent.management.spi;

import java.util.List;

public interface TenantListProvider {

    List<TenantOption> listTenants();

    record TenantOption(String id, String name) {
    }
}
