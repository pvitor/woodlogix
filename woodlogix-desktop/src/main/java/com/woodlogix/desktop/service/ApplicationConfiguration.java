package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerCatalogPort;
import com.woodlogix.application.customer.CustomerChangeNotifier;
import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingCatalogPort;
import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareCatalogPort;
import com.woodlogix.application.hardware.HardwareDeletionPort;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialCatalogPort;
import com.woodlogix.application.transaction.TransactionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public CustomerCatalog customerCatalog(CustomerCatalogPort customers,
                                           CustomerChangeNotifier notifier,
                                           TransactionPort transactions) {
        return new CustomerCatalog(customers, notifier, transactions);
    }

    @Bean
    public EdgeBandingCatalog edgeBandingCatalog(EdgeBandingCatalogPort edgeBandings,
                                                 TransactionPort transactions) {
        return new EdgeBandingCatalog(edgeBandings, transactions);
    }

    @Bean
    public MaterialCatalog materialCatalog(MaterialCatalogPort materials, TransactionPort transactions) {
        return new MaterialCatalog(materials, transactions);
    }

    @Bean
    public HardwareCatalog hardwareCatalog(HardwareCatalogPort hardware, TransactionPort transactions) {
        return new HardwareCatalog(hardware, transactions);
    }

    @Bean
    public DeleteHardware deleteHardware(HardwareDeletionPort hardware, TransactionPort transactions) {
        return new DeleteHardware(hardware, transactions);
    }
}
