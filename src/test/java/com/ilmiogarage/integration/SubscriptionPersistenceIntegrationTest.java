package com.ilmiogarage.integration;

import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.UserRole;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.UserSubscriptionRepository;
import com.ilmiogarage.repository.VehicleRepository;
import com.ilmiogarage.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PremiumGrantRepository premiumGrantRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("User viene salvato con il ruolo predefinito USER e può essere impostato ad ADMIN")
    void user_roleDefaultAndAdmin() {
        User standardUser = new User(UUID.randomUUID(), "Standard User", "standard@example.com", "hash");
        User savedStandard = userRepository.saveAndFlush(standardUser);
        assertThat(savedStandard.getRole()).isEqualTo(UserRole.USER);

        User adminUser = new User(UUID.randomUUID(), "Admin User", "admin@example.com", "hash", null, UserRole.ADMIN);
        User savedAdmin = userRepository.saveAndFlush(adminUser);
        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Vehicle supporta il flag isPrimary (true e false) e recupero del veicolo primario")
    void vehicle_isPrimaryPersistence() {
        User owner = userRepository.saveAndFlush(new User(UUID.randomUUID(), "Owner", "owner@example.com", "hash"));

        // Veicolo 1: primario
        Vehicle v1 = new Vehicle(owner, VehicleIcon.carFront, "Alfa Romeo", "Giulia", 2022, "AA111AA", 40000, true);
        vehicleRepository.saveAndFlush(v1);

        // Veicolo 2: non primario (consentito: l'utente può avere più veicoli secondari)
        Vehicle v2 = new Vehicle(owner, VehicleIcon.motorbike, "Ducati", "Monster", 2021, "BB222BB", 15000, false);
        vehicleRepository.saveAndFlush(v2);

        // Veicolo 3: non primario
        Vehicle v3 = new Vehicle(owner, VehicleIcon.scooter, "Piaggio", "Beverly", 2020, "CC333CC", 20000, false);
        vehicleRepository.saveAndFlush(v3);

        assertThat(vehicleRepository.findByOwnerIdAndIsPrimaryTrue(owner.getId())).isPresent();
        assertThat(vehicleRepository.findByOwnerIdAndIsPrimaryTrue(owner.getId()).get().getPlate()).isEqualTo("AA111AA");
        assertThat(vehicleRepository.existsByOwnerIdAndIsPrimaryTrue(owner.getId())).isTrue();
    }

    @Test
    @DisplayName("PremiumGrant consente molteplici concessioni storiche per lo stesso utente (revocate o scadute)")
    void premiumGrant_allowsHistoryForSameUser() {
        User user = userRepository.saveAndFlush(new User(UUID.randomUUID(), "Tester User", "tester@example.com", "hash"));
        Instant now = Instant.now();

        // Grant 1: revocato
        PremiumGrant grant1 = new PremiumGrant(user, GrantType.TESTER, now.minus(60, ChronoUnit.DAYS), "ADMIN", "Primo test", now.minus(30, ChronoUnit.DAYS));
        grant1.setRevokedAt(now.minus(40, ChronoUnit.DAYS));
        premiumGrantRepository.saveAndFlush(grant1);

        // Grant 2: attivo permanente (nuova concessione successiva per lo stesso utente)
        PremiumGrant grant2 = new PremiumGrant(user, GrantType.COLLABORATOR, now, "ADMIN", "Promosso a collaboratore", null);
        premiumGrantRepository.saveAndFlush(grant2);

        List<PremiumGrant> allUserGrants = premiumGrantRepository.findAllByUserIdOrderByGrantedAtDesc(user.getId());
        assertThat(allUserGrants).hasSize(2);

        List<PremiumGrant> activeGrants = premiumGrantRepository.findValidGrants(user.getId(), now);
        assertThat(activeGrants).hasSize(1);
        assertThat(activeGrants.get(0).getGrantType()).isEqualTo(GrantType.COLLABORATOR);
    }

    @Test
    @DisplayName("Casi 1-7: Validità abbonamenti per status (ACTIVE, IN_GRACE_PERIOD, CANCELED, ON_HOLD, PAUSED, EXPIRED) e scadenza")
    void subscription_statusesAndExpirationRules() {
        User user = userRepository.saveAndFlush(new User(UUID.randomUUID(), "SubUser", "subuser@example.com", "hash"));
        Instant now = Instant.now();

        // 1. ACTIVE con scadenza futura -> Valido (Premium)
        UserSubscription activeFuture = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_active_fut", "tok_active_fut", "order1",
                SubscriptionStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS), true
        );
        userSubscriptionRepository.saveAndFlush(activeFuture);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).hasSize(1);
        assertThat(subscriptionService.isPremium(user.getId())).isTrue();
        userSubscriptionRepository.delete(activeFuture);
        userSubscriptionRepository.flush();

        // 2. ACTIVE con scadenza passata -> Non valido (Free)
        UserSubscription activePast = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_active_past", "tok_active_past", "order2",
                SubscriptionStatus.ACTIVE, now.minus(35, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS), true
        );
        userSubscriptionRepository.saveAndFlush(activePast);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).isEmpty();
        assertThat(subscriptionService.isPremium(user.getId())).isFalse();
        userSubscriptionRepository.delete(activePast);
        userSubscriptionRepository.flush();

        // 3. ACTIVE con expiresAt = NULL -> Non valido (Free)
        UserSubscription activeNullExpiry = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_active_null", "tok_active_null", "order3",
                SubscriptionStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), null, true
        );
        userSubscriptionRepository.saveAndFlush(activeNullExpiry);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).isEmpty();
        assertThat(subscriptionService.isPremium(user.getId())).isFalse();
        userSubscriptionRepository.delete(activeNullExpiry);
        userSubscriptionRepository.flush();

        // 4. IN_GRACE_PERIOD con scadenza futura -> Valido (Premium)
        UserSubscription graceFuture = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_grace", "tok_grace", "order4",
                SubscriptionStatus.IN_GRACE_PERIOD, now.minus(30, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), true
        );
        userSubscriptionRepository.saveAndFlush(graceFuture);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).hasSize(1);
        assertThat(subscriptionService.isPremium(user.getId())).isTrue();
        userSubscriptionRepository.delete(graceFuture);
        userSubscriptionRepository.flush();

        // 5. CANCELED con scadenza futura -> Valido (Premium fino a termine periodo pagato)
        UserSubscription canceledFuture = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_canceled_fut", "tok_canceled_fut", "order5",
                SubscriptionStatus.CANCELED, now.minus(10, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), false
        );
        userSubscriptionRepository.saveAndFlush(canceledFuture);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).hasSize(1);
        assertThat(subscriptionService.isPremium(user.getId())).isTrue();
        userSubscriptionRepository.delete(canceledFuture);
        userSubscriptionRepository.flush();

        // 6. CANCELED con scadenza passata -> Non valido (Free)
        UserSubscription canceledPast = new UserSubscription(
                user, "GOOGLE_PLAY", "prod_canceled_past", "tok_canceled_past", "order6",
                SubscriptionStatus.CANCELED, now.minus(40, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS), false
        );
        userSubscriptionRepository.saveAndFlush(canceledPast);
        assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).isEmpty();
        assertThat(subscriptionService.isPremium(user.getId())).isFalse();
        userSubscriptionRepository.delete(canceledPast);
        userSubscriptionRepository.flush();

        // 7. ON_HOLD, PAUSED, EXPIRED anche con data futura -> Non concedono il Premium
        for (SubscriptionStatus status : List.of(SubscriptionStatus.ON_HOLD, SubscriptionStatus.PAUSED, SubscriptionStatus.EXPIRED)) {
            UserSubscription nonEligible = new UserSubscription(
                    user, "GOOGLE_PLAY", "prod_" + status, "tok_" + status, "order_" + status,
                    status, now.minus(5, ChronoUnit.DAYS), now.plus(20, ChronoUnit.DAYS), true
            );
            userSubscriptionRepository.saveAndFlush(nonEligible);
            assertThat(userSubscriptionRepository.findActiveSubscriptions(user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now)).isEmpty();
            assertThat(subscriptionService.isPremium(user.getId())).isFalse();
            userSubscriptionRepository.delete(nonEligible);
            userSubscriptionRepository.flush();
        }
    }

    @Test
    @DisplayName("Caso 8: PremiumGrant permanente con expiresAt = NULL concede il Premium")
    void premiumGrant_permanentWithNullExpiry_grantsPremium() {
        User user = userRepository.saveAndFlush(new User(UUID.randomUUID(), "DevUser", "devuser@example.com", "hash"));
        Instant now = Instant.now();

        PremiumGrant devGrant = new PremiumGrant(user, GrantType.DEVELOPER, now.minus(1, ChronoUnit.DAYS), "SYSTEM", "Dev permanente", null);
        premiumGrantRepository.saveAndFlush(devGrant);

        assertThat(premiumGrantRepository.findValidGrants(user.getId(), now)).hasSize(1);
        assertThat(subscriptionService.isPremium(user.getId())).isTrue();
        assertThat(subscriptionService.getUserPlanInfo(user.getId()).isPermanent()).isTrue();
    }

    @Test
    @DisplayName("Caso 9: Due abbonamenti validi -> selezione deterministica (scadenza più lontana)")
    void subscription_deterministicSelectionMultipleActive() {
        User user = userRepository.saveAndFlush(new User(UUID.randomUUID(), "MultiSub", "multi@example.com", "hash"));
        Instant now = Instant.now();

        UserSubscription subMonthly = new UserSubscription(
                user, "GOOGLE_PLAY", "monthly", "tok_monthly", "orderA",
                SubscriptionStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS), true
        );
        UserSubscription subAnnual = new UserSubscription(
                user, "GOOGLE_PLAY", "annual", "tok_annual", "orderB",
                SubscriptionStatus.ACTIVE, now.minus(10, ChronoUnit.DAYS), now.plus(355, ChronoUnit.DAYS), true
        );
        userSubscriptionRepository.saveAndFlush(subMonthly);
        userSubscriptionRepository.saveAndFlush(subAnnual);

        List<UserSubscription> active = userSubscriptionRepository.findActiveSubscriptions(
                user.getId(), SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES, now);

        assertThat(active).hasSize(2);
        // Primo elemento deterministico: scadenza più lontana (subAnnual)
        assertThat(active.get(0).getProductId()).isEqualTo("annual");
        assertThat(subscriptionService.getUserPlanInfo(user.getId()).productId()).isEqualTo("annual");
    }

    @Test
    @DisplayName("Caso 10: Due record con stesso provider e token non null -> violazione indice univoco (secondo rifiutato)")
    void purchaseToken_duplicateNonNullableRejected() {
        User user1 = userRepository.saveAndFlush(new User(UUID.randomUUID(), "User1", "u1@example.com", "hash"));
        User user2 = userRepository.saveAndFlush(new User(UUID.randomUUID(), "User2", "u2@example.com", "hash"));
        Instant now = Instant.now();

        UserSubscription sub1 = new UserSubscription(
                user1, "GOOGLE_PLAY", "premium", "SHARED_TOKEN_123", "order1",
                SubscriptionStatus.ACTIVE, now, now.plus(30, ChronoUnit.DAYS), true
        );
        userSubscriptionRepository.saveAndFlush(sub1);

        // Tentativo di associare lo stesso token a un altro utente per lo stesso provider
        UserSubscription sub2 = new UserSubscription(
                user2, "GOOGLE_PLAY", "premium", "SHARED_TOKEN_123", "order2",
                SubscriptionStatus.ACTIVE, now, now.plus(30, ChronoUnit.DAYS), true
        );

        assertThatThrownBy(() -> userSubscriptionRepository.saveAndFlush(sub2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Caso 11: Più record con purchase token NULL per lo stesso provider -> inserimento consentito")
    void purchaseToken_multipleNullTokensAllowed() {
        User user1 = userRepository.saveAndFlush(new User(UUID.randomUUID(), "UserNull1", "unull1@example.com", "hash"));
        User user2 = userRepository.saveAndFlush(new User(UUID.randomUUID(), "UserNull2", "unull2@example.com", "hash"));
        Instant now = Instant.now();

        UserSubscription subNull1 = new UserSubscription(
                user1, "GOOGLE_PLAY", "manual_promo", null, "orderNull1",
                SubscriptionStatus.ACTIVE, now, now.plus(30, ChronoUnit.DAYS), false
        );
        UserSubscription subNull2 = new UserSubscription(
                user2, "GOOGLE_PLAY", "manual_promo", null, "orderNull2",
                SubscriptionStatus.ACTIVE, now, now.plus(30, ChronoUnit.DAYS), false
        );

        userSubscriptionRepository.saveAndFlush(subNull1);
        userSubscriptionRepository.saveAndFlush(subNull2);

        assertThat(userSubscriptionRepository.findById(subNull1.getId())).isPresent();
        assertThat(userSubscriptionRepository.findById(subNull2.getId())).isPresent();
    }
}
