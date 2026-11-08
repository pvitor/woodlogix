package com.woodlogix.desktop.service;

import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareCatalogPort;
import com.woodlogix.application.hardware.HardwareDeletionPort;
import com.woodlogix.application.transaction.TransactionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public HardwareCatalog hardwareCatalog(HardwareCatalogPort hardware, TransactionPort transactions) {
        return new HardwareCatalog(hardware, transactions);
    }

    @Bean
    public DeleteHardware deleteHardware(HardwareDeletionPort hardware, TransactionPort transactions) {
        return new DeleteHardware(hardware, transactions);
    }
}
