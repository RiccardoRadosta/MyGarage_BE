package com.ilmiogarage.integration;

import com.ilmiogarage.dto.vehicle.CreateVehicleRequest;
import com.ilmiogarage.dto.vehicle.VehicleResponse;
import com.ilmiogarage.exception.UpgradeRequiredException;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.VehicleRepository;
import com.ilmiogarage.service.VehicleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VehicleCreationConcurrencyIntegrationTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @DisplayName("Concorrenza: due richieste simultanee di creazione per utente Free non possono produrre 2 veicoli")
    void concurrentVehicleCreation_forFreeUser_onlyOneSucceeds() throws Exception {
        // 1. Crea utente Free direttamente
        String email = "concurrent.user." + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(new User(UUID.randomUUID(), "Concurrent User", email, "passwordHash"));
        UUID userId = user.getId();

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger upgradeRequiredCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Sincronizzazione partenza simultanea dei thread
                    CreateVehicleRequest req = new CreateVehicleRequest(
                            VehicleIcon.carFront, "Brand" + index, "Model" + index, 2021, "CC" + index + "00ZZ", 10000 + (index * 1000));
                    VehicleResponse res = vehicleService.createVehicle(userId, req);
                    if (res != null) {
                        successCount.incrementAndGet();
                    }
                } catch (UpgradeRequiredException ure) {
                    upgradeRequiredCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Avvio simultaneo
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(upgradeRequiredCount.get()).isEqualTo(1);

        long dbCount = vehicleRepository.countByOwnerId(userId);
        assertThat(dbCount).isEqualTo(1);
    }
}
