package com.ilmiogarage.service;

import com.ilmiogarage.dto.subscription.UserPlanInfo;
import com.ilmiogarage.model.entity.PremiumGrant;
import com.ilmiogarage.model.entity.User;
import com.ilmiogarage.model.entity.UserSubscription;
import com.ilmiogarage.model.entity.Vehicle;
import com.ilmiogarage.model.enums.SubscriptionStatus;
import com.ilmiogarage.model.enums.SubscriptionTier;
import com.ilmiogarage.repository.PremiumGrantRepository;
import com.ilmiogarage.repository.UserRepository;
import com.ilmiogarage.repository.UserSubscriptionRepository;
import com.ilmiogarage.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    public static final List<SubscriptionStatus> ELIGIBLE_SUBSCRIPTION_STATUSES = List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.IN_GRACE_PERIOD,
            SubscriptionStatus.CANCELED
    );

    private final PremiumGrantRepository premiumGrantRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public SubscriptionService(
            PremiumGrantRepository premiumGrantRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository) {
        this(premiumGrantRepository, userSubscriptionRepository, vehicleRepository, userRepository, Clock.systemUTC());
    }

    // Costruttore con Clock personalizzabile per facilitare i test di scadenza temporale
    public SubscriptionService(
            PremiumGrantRepository premiumGrantRepository,
            UserSubscriptionRepository userSubscriptionRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            Clock clock) {
        this.premiumGrantRepository = premiumGrantRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Determina il piano effettivo (FREE o PREMIUM) per l'utente.
     */
    @Transactional(readOnly = true)
    public SubscriptionTier getEffectiveTier(UUID userId) {
        if (userId == null) {
            return SubscriptionTier.FREE;
        }
        return isPremium(userId) ? SubscriptionTier.PREMIUM : SubscriptionTier.FREE;
    }

    /**
     * Verifica se l'utente ha diritto ai vantaggi Premium (tramite Grant o Abbonamento attivo).
     */
    @Transactional(readOnly = true)
    public boolean isPremium(UUID userId) {
        if (userId == null) {
            return false;
        }
        Instant now = Instant.now(clock);

        // 1. Precedenza assoluta: Grant valido (DEVELOPER, TESTER, COLLABORATOR)
        List<PremiumGrant> validGrants = premiumGrantRepository.findValidGrants(userId, now);
        if (!validGrants.isEmpty()) {
            return true;
        }

        // 2. Abbonamento Google Play / store valido (ACTIVE, IN_GRACE_PERIOD, CANCELED con scadenza futura)
        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptions(
                userId, ELIGIBLE_SUBSCRIPTION_STATUSES, now);
        return !activeSubs.isEmpty();
    }

    /**
     * Recupera la data di inizio del periodo Premium più recente (da subscription o grant).
     * Se l'utente non ha mai avuto periodi Premium, restituisce Instant.EPOCH.
     */
    @Transactional(readOnly = true)
    public Instant findLastPremiumStartedAt(UUID userId) {
        if (userId == null) {
            return Instant.EPOCH;
        }
        Optional<Instant> maxSubStart = userSubscriptionRepository.findMaxStartedAtByUserId(userId);
        Optional<Instant> maxGrantStart = premiumGrantRepository.findMaxGrantedAtByUserId(userId);

        if (maxSubStart.isEmpty() && maxGrantStart.isEmpty()) {
            return Instant.EPOCH;
        }
        if (maxSubStart.isPresent() && maxGrantStart.isPresent()) {
            return maxSubStart.get().isAfter(maxGrantStart.get()) ? maxSubStart.get() : maxGrantStart.get();
        }
        return maxSubStart.orElseGet(maxGrantStart::get);
    }

    /**
     * Determina se l'utente è tenuto a effettuare la scelta obbligatoria del veicolo principale.
     */
    @Transactional(readOnly = true)
    public boolean isPrimaryVehicleSelectionRequired(UUID userId) {
        if (userId == null || isPremium(userId)) {
            return false;
        }
        int vehicleCount = vehicleRepository.countByOwnerId(userId);
        if (vehicleCount <= 1) {
            return false;
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        Instant selectedAt = userOpt.get().getPrimaryVehicleSelectedAt();
        if (selectedAt == null) {
            return true;
        }
        Instant lastPremiumStartedAt = findLastPremiumStartedAt(userId);
        if (lastPremiumStartedAt == null) {
            return false;
        }
        Instant normalizedSelectedAt = selectedAt.truncatedTo(ChronoUnit.MICROS);
        Instant normalizedStartedAt = lastPremiumStartedAt.truncatedTo(ChronoUnit.MICROS);
        return normalizedSelectedAt.isBefore(normalizedStartedAt);
    }

    /**
     * Restituisce le informazioni dettagliate sull'origine e lo stato del piano dell'utente.
     */
    @Transactional(readOnly = true)
    public UserPlanInfo getUserPlanInfo(UUID userId) {
        if (userId == null) {
            return UserPlanInfo.free();
        }
        Instant now = Instant.now(clock);

        UUID primaryVehicleId = vehicleRepository.findByOwnerIdAndIsPrimaryTrue(userId)
                .map(Vehicle::getId)
                .orElse(null);

        // 1. Controllo Grant
        List<PremiumGrant> validGrants = premiumGrantRepository.findValidGrants(userId, now);
        if (!validGrants.isEmpty()) {
            PremiumGrant primaryGrant = validGrants.get(0);
            return UserPlanInfo.fromGrant(primaryGrant.getGrantType(), primaryGrant.getExpiresAt(), primaryVehicleId);
        }

        // 2. Controllo Abbonamento Google Play / store
        List<UserSubscription> activeSubs = userSubscriptionRepository.findActiveSubscriptions(
                userId, ELIGIBLE_SUBSCRIPTION_STATUSES, now);
        if (!activeSubs.isEmpty()) {
            UserSubscription primarySub = activeSubs.get(0);
            return UserPlanInfo.fromSubscription(
                    primarySub.getProductId(),
                    primarySub.getStatus(),
                    primarySub.getExpiresAt(),
                    primarySub.isAutoRenewing(),
                    primaryVehicleId
            );
        }

        // 3. Fallback: piano FREE
        boolean requiresPrimarySelection = isPrimaryVehicleSelectionRequired(userId);
        return UserPlanInfo.free(primaryVehicleId, requiresPrimarySelection);
    }
}
