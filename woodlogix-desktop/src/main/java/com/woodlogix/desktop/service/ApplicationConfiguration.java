package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerCatalogPort;
import com.woodlogix.application.customer.CustomerChangeNotifier;
import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingCatalogPort;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentCatalogPort;
import com.woodlogix.application.environment.EnvironmentChangeNotifier;
import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareCatalogPort;
import com.woodlogix.application.hardware.HardwareDeletionPort;
import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialCatalogPort;
import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesPipelinePort;
import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpiecePort;
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
    public EnvironmentCatalog environmentCatalog(EnvironmentCatalogPort environments,
                                                 EnvironmentChangeNotifier notifier,
                                                 TransactionPort transactions) {
        return new EnvironmentCatalog(environments, notifier, transactions);
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

    @Bean
    public SalesPipeline salesPipeline(SalesPipelinePort projects, TransactionPort transactions) {
        return new SalesPipeline(projects, transactions);
    }

    @Bean
    public ProjectWorkpiece projectWorkpiece(ProjectWorkpiecePort workpieces,
                                             TransactionPort transactions) {
        return new ProjectWorkpiece(workpieces, transactions);
    }
}
