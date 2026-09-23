package com.borrowbox.integration;

import com.borrowbox.dto.CommunityCreateRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.Community;
import com.borrowbox.entity.CommunityListing;
import com.borrowbox.entity.CommunityType;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.Membership;
import com.borrowbox.entity.MembershipRole;
import com.borrowbox.entity.MembershipStatus;
import com.borrowbox.entity.MembershipVerificationMethod;
import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CommunityListingRepository;
import com.borrowbox.repository.CommunityRepository;
import com.borrowbox.repository.MembershipRepository;
import com.borrowbox.repository.TransactionRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityService;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared helpers for the V2.4.2 integration tests. Every helper builds
 * isolated rows (fresh users, a brand-new community, fresh asset/listing and
 * direct transactions) so assertions are exact regardless of what else is
 * already committed in the shared test database. Test classes run
 * {@link org.springframework.transaction.annotation.Transactional}, so all
 * rows are rolled back after each test.
 */
final class V242IntegrationFixture {

    private V242IntegrationFixture() {
    }

    static User newUser(UserRepository repository, String prefix) {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        return repository.findByEmail(email).orElseGet(() -> {
            User user = new User(prefix, email);
            user.setPasswordHash("test-password");
            user.setStatus(UserStatus.ACTIVE);
            return repository.save(user);
        });
    }

    static Community newCommunity(CommunityService communityService,
                                  CommunityRepository repository, User manager) {
        var created = communityService.createCommunity(
                new CommunityCreateRequest(
                        "V2.4.2 " + UUID.randomUUID(), null, CommunityType.CLUB, null, null, null, null),
                manager);
        return repository.findById(created.id()).orElseThrow();
    }

    static Membership joinActive(MembershipRepository repository,
                                 User user, Community community, MembershipRole role) {
        if (repository.existsByUserIdAndCommunityId(user.getId(), community.getId())) {
            return repository.findByUserIdAndCommunityId(user.getId(), community.getId()).orElseThrow();
        }
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setCommunity(community);
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setVerificationMethod(MembershipVerificationMethod.ADMIN);
        membership.setVerifiedBy(user);
        membership.setVerifiedAt(LocalDateTime.now());
        membership.setJoinedAt(LocalDateTime.now());
        return repository.save(membership);
    }

    static Membership joinPending(MembershipRepository repository,
                                  User user, Community community) {
        Membership membership = new Membership();
        membership.setUser(user);
        membership.setCommunity(community);
        membership.setRole(MembershipRole.MEMBER);
        membership.setStatus(MembershipStatus.PENDING);
        membership.setVerificationMethod(MembershipVerificationMethod.MANAGER_APPROVAL);
        return repository.save(membership);
    }

    static Asset newAsset(AssetRepository repository, User owner, String title) {
        Asset asset = new Asset();
        asset.setOwner(owner);
        asset.setTitle(title);
        asset.setStatus(AssetStatus.ACTIVE);
        return repository.save(asset);
    }

    static CommunityListing newListing(CommunityListingRepository repository,
                                       Asset asset, Community community) {
        CommunityListing listing = new CommunityListing();
        listing.setAsset(asset);
        listing.setCommunity(community);
        listing.setListingStatus(ListingStatus.LISTED);
        listing.setListedAt(LocalDateTime.now());
        return repository.save(listing);
    }

    static Transaction newTransaction(TransactionRepository repository,
                                      Community community,
                                      CommunityListing listing, Asset asset,
                                      User borrower, User lender,
                                      TransactionStatus state,
                                      LocalDateTime dueAt, LocalDateTime completedAt) {
        Transaction txn = new Transaction();
        txn.setCommunity(community);
        txn.setListing(listing);
        txn.setAsset(asset);
        txn.setBorrower(borrower);
        txn.setLender(lender);
        txn.setState(state);
        txn.setPurpose("V2.4.2 integration fixture");
        txn.setRequestedDurationDays(1);
        if (dueAt != null) {
            txn.setStartedAt(dueAt.minusDays(2));
            txn.setDueAt(dueAt);
            txn.setOriginalDueAt(dueAt);
        }
        txn.setCompletedAt(completedAt);
        return repository.save(txn);
    }
}