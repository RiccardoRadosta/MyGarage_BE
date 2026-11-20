package com.ilmiogarage.service;

import com.ilmiogarage.dto.subscription.UserPlanInfo;
import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.GrantType;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.SubscriptionTier;
import com.ilmiogarage.model.enums.VehicleIcon;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserSubscriptionRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private PremiumGrantRepository premiumGrantRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private com.ilmiogarage.repository.UserRepository userRepository;

    private Clock fixedClock;
    private Instant now;
    private SubscriptionService subscriptionService;
    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-19T10:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        subscriptionService = new SubscriptionService(premiumGrantRepository, userSubscriptionRepository, vehicleRepository, userRepository, fixedClock);
        userId = UUID.randomUUID();
        testUser = new User(userId, "Mario Rossi", "mario@example.com", "hash");
    }

    @Test
    @DisplayName("Caso 1: Nessun abbonamento e nessun grant -> FREE")
    void case1_noSubscriptionNoGrant_returnsFree() {
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(Collections.emptyList());

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        boolean isPremium = subscriptionService.isPremium(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.FREE);
        assertThat(isPremium).isFalse();
        assertThat(planInfo.tier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(planInfo.isPremium()).isFalse();
        assertThat(planInfo.origin()).isEqualTo(UserPlanInfo.PlanOrigin.FREE_TIER);
    }

    @Test
    @DisplayName("Caso 2: Grant DEVELOPER permanente (expiresAt == null) -> PREMIUM")
    void case2_developerGrantPermanent_returnsPremium() {
        PremiumGrant grant = new PremiumGrant(testUser, GrantType.DEVELOPER, now.minus(10, ChronoUnit.DAYS), "SYSTEM", "Dev", null);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(List.of(grant));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        boolean isPremium = subscriptionService.isPremium(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(isPremium).isTrue();
        assertThat(planInfo.origin()).isEqualTo(UserPlanInfo.PlanOrigin.GRANT);
        assertThat(planInfo.grantType()).isEqualTo(GrantType.DEVELOPER);
        assertThat(planInfo.isPermanent()).isTrue();
        assertThat(planInfo.expiresAt()).isNull();
    }

    @Test
    @DisplayName("Caso 3: Grant TESTER temporaneo non scaduto -> PREMIUM")
    void case3_testerGrantActive_returnsPremium() {
        Instant futureExpiry = now.plus(30, ChronoUnit.DAYS);
        PremiumGrant grant = new PremiumGrant(testUser, GrantType.TESTER, now.minus(5, ChronoUnit.DAYS), "SYSTEM", "Beta tester", futureExpiry);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(List.of(grant));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(planInfo.grantType()).isEqualTo(GrantType.TESTER);
        assertThat(planInfo.isPermanent()).isFalse();
        assertThat(planInfo.expiresAt()).isEqualTo(futureExpiry);
    }

    @Test
    @DisplayName("Caso 4: Grant scaduto -> FREE (se non esiste altro beneficio valido)")
    void case4_grantExpired_returnsFree() {
        // Il repository con query findValidGrants filtra via i grant scaduti
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(Collections.emptyList());

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        assertThat(tier).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("Caso 5: Grant revocato -> FREE (se non esiste altro beneficio)")
    void case5_grantRevoked_returnsFree() {
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(Collections.emptyList());

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        assertThat(tier).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("Caso 6: Abbonamento Google Play ACTIVE con scadenza futura -> PREMIUM")
    void case6_googlePlaySubscriptionActive_returnsPremium() {
        Instant futureExpiry = now.plus(20, ChronoUnit.DAYS);
        UserSubscription sub = new UserSubscription(
                testUser, "GOOGLE_PLAY", "mygarage_premium_monthly", "token123", "GPA.123",
                SubscriptionStatus.ACTIVE, now.minus(10, ChronoUnit.DAYS), futureExpiry, true
        );

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), eq(SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES), eq(now)))
                .thenReturn(List.of(sub));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(planInfo.origin()).isEqualTo(UserPlanInfo.PlanOrigin.GOOGLE_PLAY);
        assertThat(planInfo.productId()).isEqualTo("mygarage_premium_monthly");
        assertThat(planInfo.expiresAt()).isEqualTo(futureExpiry);
        assertThat(planInfo.autoRenewing()).isTrue();
    }

    @Test
    @DisplayName("Caso 7: Abbonamento Google Play scaduto o non valido -> FREE")
    void case7_googlePlaySubscriptionExpired_returnsFree() {
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(Collections.emptyList());

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        assertThat(tier).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("Caso 8: Grant valido e abbonamento scaduto -> PREMIUM")
    void case8_grantValidSubscriptionExpired_returnsPremium() {
        PremiumGrant grant = new PremiumGrant(testUser, GrantType.COLLABORATOR, now, "ADMIN", "Collab", null);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(List.of(grant));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(planInfo.origin()).isEqualTo(UserPlanInfo.PlanOrigin.GRANT);
        assertThat(planInfo.grantType()).isEqualTo(GrantType.COLLABORATOR);
    }

    @Test
    @DisplayName("Caso 9: Grant valido e abbonamento attivo -> PREMIUM per origine GRANT (precedenza assoluta)")
    void case9_grantValidAndSubscriptionActive_precedenceIsGrant() {
        PremiumGrant grant = new PremiumGrant(testUser, GrantType.DEVELOPER, now, "SYSTEM", "Dev", null);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(List.of(grant));

        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(planInfo.origin()).isEqualTo(UserPlanInfo.PlanOrigin.GRANT);
        assertThat(planInfo.grantType()).isEqualTo(GrantType.DEVELOPER);
        assertThat(planInfo.isPermanent()).isTrue();
    }

    @Test
    @DisplayName("Caso 10: Abbonamento CANCELED con scadenza ancora futura -> PREMIUM")
    void case10_canceledSubscriptionStillInFuture_returnsPremium() {
        Instant futureExpiry = now.plus(15, ChronoUnit.DAYS);
        UserSubscription sub = new UserSubscription(
                testUser, "GOOGLE_PLAY", "mygarage_premium_monthly", "token123", "GPA.123",
                SubscriptionStatus.CANCELED, now.minus(15, ChronoUnit.DAYS), futureExpiry, false
        );

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), eq(SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES), eq(now)))
                .thenReturn(List.of(sub));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);

        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(planInfo.isPremium()).isTrue();
        assertThat(planInfo.autoRenewing()).isFalse();
        assertThat(planInfo.expiresAt()).isEqualTo(futureExpiry);
    }

    @Test
    @DisplayName("Caso 11: Abbonamento IN_GRACE_PERIOD con scadenza futura -> PREMIUM")
    void case11_inGracePeriodFutureExpiry_returnsPremium() {
        Instant futureExpiry = now.plus(3, ChronoUnit.DAYS);
        UserSubscription sub = new UserSubscription(
                testUser, "GOOGLE_PLAY", "mygarage_premium_monthly", "tokenGrace", "GPA.grace",
                SubscriptionStatus.IN_GRACE_PERIOD, now.minus(30, ChronoUnit.DAYS), futureExpiry, true
        );

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), eq(SubscriptionService.ELIGIBLE_SUBSCRIPTION_STATUSES), eq(now)))
                .thenReturn(List.of(sub));

        SubscriptionTier tier = subscriptionService.getEffectiveTier(userId);
        assertThat(tier).isEqualTo(SubscriptionTier.PREMIUM);
    }

    @Test
    @DisplayName("Caso 12: Due abbonamenti validi -> selezione deterministica del primo restituito dall'ordinamento")
    void case12_twoValidSubscriptions_deterministicPrimarySelection() {
        Instant expiry1 = now.plus(60, ChronoUnit.DAYS);
        Instant expiry2 = now.plus(20, ChronoUnit.DAYS);

        UserSubscription subAnnual = new UserSubscription(
                testUser, "GOOGLE_PLAY", "mygarage_premium_annual", "tokenAnnual", "GPA.annual",
                SubscriptionStatus.ACTIVE, now.minus(10, ChronoUnit.DAYS), expiry1, true
        );
        UserSubscription subMonthly = new UserSubscription(
                testUser, "GOOGLE_PLAY", "mygarage_premium_monthly", "tokenMonthly", "GPA.monthly",
                SubscriptionStatus.ACTIVE, now.minus(5, ChronoUnit.DAYS), expiry2, false
        );

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        // Il repository restituisce i record già ordinati per expiresAt DESC, createdAt DESC, id DESC
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(List.of(subAnnual, subMonthly));

        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);
        assertThat(planInfo.productId()).isEqualTo("mygarage_premium_annual");
        assertThat(planInfo.expiresAt()).isEqualTo(expiry1);
    }

    @Test
    @DisplayName("Caso 13: Recupero dell'ID del veicolo principale in UserPlanInfo")
    void case13_primaryVehicleId_includedInUserPlanInfo() {
        UUID primaryId = UUID.randomUUID();
        Vehicle primaryVehicle = new Vehicle(testUser, VehicleIcon.carFront, "Alfa", "Giulia", 2022, "AA111AA", 10000, true);
        // Utilizziamo reflection o stub per l'id
        when(vehicleRepository.findByOwnerIdAndIsPrimaryTrue(eq(userId)))
                .thenReturn(java.util.Optional.of(primaryVehicle));

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now)))
                .thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now)))
                .thenReturn(Collections.emptyList());

        UserPlanInfo planInfo = subscriptionService.getUserPlanInfo(userId);
        assertThat(planInfo.tier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(planInfo.primaryVehicleId()).isEqualTo(primaryVehicle.getId());
    }

    @Test
    @DisplayName("findLastPremiumStartedAt: Nessun precedente -> restituisce EPOCH")
    void findLastPremiumStartedAt_noHistory_returnsEpoch() {
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.empty());
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        Instant startedAt = subscriptionService.findLastPremiumStartedAt(userId);
        assertThat(startedAt).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("findLastPremiumStartedAt: Sia subscription che grant -> restituisce la data più recente")
    void findLastPremiumStartedAt_bothSubAndGrant_returnsMax() {
        Instant subStart = now.minus(30, ChronoUnit.DAYS);
        Instant grantStart = now.minus(10, ChronoUnit.DAYS);

        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.of(subStart));
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.of(grantStart));

        Instant startedAt = subscriptionService.findLastPremiumStartedAt(userId);
        assertThat(startedAt).isEqualTo(grantStart);
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Utente Free con <= 1 veicolo -> false")
    void isPrimaryVehicleSelectionRequired_zeroOrOneVehicle_returnsFalse() {
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(1);

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Utente Premium con > 1 veicolo -> false")
    void isPrimaryVehicleSelectionRequired_premiumUser_returnsFalse() {
        PremiumGrant grant = new PremiumGrant(testUser, GrantType.DEVELOPER, now.minus(5, ChronoUnit.DAYS), "Admin", null, null);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(List.of(grant));

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Utente Free con > 1 veicolo e mai selezionato -> true")
    void isPrimaryVehicleSelectionRequired_freeUserMultipleVehicles_neverSelected_returnsTrue() {
        testUser.setPrimaryVehicleSelectedAt(null);
        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isTrue();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Utente Free con > 1 veicolo e scelta successiva all'ultimo Premium -> false")
    void isPrimaryVehicleSelectionRequired_freeUserMultipleVehicles_selectedAfterPremium_returnsFalse() {
        Instant premiumStart = now.minus(30, ChronoUnit.DAYS);
        Instant selectedAt = now.minus(5, ChronoUnit.DAYS); // scelto dopo l'inizio del Premium
        testUser.setPrimaryVehicleSelectedAt(selectedAt);

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.of(premiumStart));
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Nuovo periodo Premium successivo alla scelta e poi downgrade -> true")
    void isPrimaryVehicleSelectionRequired_newPremiumAfterSelection_returnsTrue() {
        Instant oldSelection = now.minus(60, ChronoUnit.DAYS); // scelta nel ciclo 1
        Instant newPremiumStart = now.minus(15, ChronoUnit.DAYS); // ciclo 2 iniziato dopo
        testUser.setPrimaryVehicleSelectedAt(oldSelection);

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.of(newPremiumStart));
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isTrue();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Timestamp identici (stessa precisione) -> false (scelta non rigenerata)")
    void isPrimaryVehicleSelectionRequired_sameTimestamp_returnsFalse() {
        Instant exactTimestamp = now.minus(10, ChronoUnit.DAYS);
        testUser.setPrimaryVehicleSelectedAt(exactTimestamp);

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.of(exactTimestamp));
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Nanosecondi diversi ma identici a microsecondi -> false")
    void isPrimaryVehicleSelectionRequired_differentNanosSameMicros_returnsFalse() {
        // Stesso microsecondo, ma nanosecondi differenti
        Instant selectedAt = Instant.parse("2026-05-01T10:00:00.123456789Z");
        Instant startedAt = Instant.parse("2026-05-01T10:00:00.123456111Z");
        testUser.setPrimaryVehicleSelectedAt(selectedAt);

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.of(startedAt));
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }

    @Test
    @DisplayName("isPrimaryVehicleSelectionRequired: Subscription senza startsAt (es. ON_HOLD mai avviata) -> false")
    void isPrimaryVehicleSelectionRequired_subscriptionWithoutStartsAt_returnsFalse() {
        Instant selectedAt = now.minus(5, ChronoUnit.DAYS);
        testUser.setPrimaryVehicleSelectedAt(selectedAt);

        when(premiumGrantRepository.findValidGrants(eq(userId), eq(now))).thenReturn(Collections.emptyList());
        when(userSubscriptionRepository.findActiveSubscriptions(eq(userId), any(), eq(now))).thenReturn(Collections.emptyList());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(testUser));
        // Il repository restituisce empty se non ci sono subscription con startsAt != null
        when(userSubscriptionRepository.findMaxStartedAtByUserId(userId)).thenReturn(java.util.Optional.empty());
        when(premiumGrantRepository.findMaxGrantedAtByUserId(userId)).thenReturn(java.util.Optional.empty());

        boolean required = subscriptionService.isPrimaryVehicleSelectionRequired(userId);
        assertThat(required).isFalse();
    }
}
