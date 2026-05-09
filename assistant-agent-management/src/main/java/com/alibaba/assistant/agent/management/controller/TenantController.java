package com.alibaba.assistant.agent.management.controller;

import com.alibaba.assistant.agent.management.spi.TenantListProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/exp-console/api/tenants")
public class TenantController {

    private final TenantListProvider tenantListProvider;

    public TenantController(TenantListProvider tenantListProvider) {
        this.tenantListProvider = tenantListProvider;
    }

    @GetMapping
    public List<TenantListProvider.TenantOption> listTenants() {
        List<TenantListProvider.TenantOption> tenants = tenantListProvider.listTenants();
        if (tenants == null || tenants.isEmpty()) {
            return Collections.emptyList();
        }
        return tenants.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.id() != null && !item.id().isBlank())
                .toList();
    }
}
