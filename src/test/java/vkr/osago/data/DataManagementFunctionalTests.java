package vkr.osago.data;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataManagementFunctionalTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("osago_test")
            .withUsername("test")
            .withPassword("test");

    private DataManagementService service;
    private JdbcTemplate jdbc;

    @BeforeAll
    void init() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = new JdbcTemplate(dataSource);
        service = new DataManagementService(jdbc, dataSource);
    }

    @BeforeEach
    void cleanupRuntimeData() {
        jdbc.execute(
                "truncate table insurance.notifications, insurance.payments, insurance.claim_attachments, insurance.claims, " +
                        "insurance.application_attachments, insurance.policy_applications, insurance.policy_drivers, " +
                        "insurance.policy_versions, insurance.policies, insurance.vehicles, insurance.agent_profiles, " +
                        "insurance.client_driver_info, insurance.insured_person_profiles, insurance.chat_messages, insurance.chats, " +
                        "insurance.user_roles, insurance.users restart identity cascade"
        );
    }

    @Test
    void migrationsAndSchemaShouldBeApplied() {
        Integer migrationCount = jdbc.queryForObject(
                "select count(*) from public.flyway_schema_history where success = true",
                Integer.class
        );

        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 5);
        assertTrue(service.tableExists("insurance", "users"));
        assertTrue(service.tableExists("insurance", "policies"));
        assertTrue(service.tableExists("insurance", "policy_applications"));
        assertTrue(service.tableExists("insurance", "claims"));
        assertTrue(service.tableExists("insurance", "payments"));
        assertTrue(service.tableExists("insurance", "notifications"));
    }

    @Test
    void shouldSupportCrudForKeyEntities() {
        long userId = service.createUser("dm-user1@test.local");
        long vehicleId = service.createVehicle(userId, "A100AA196", "VIN-DM-0001");
        long policyId = service.createPolicy(
                userId,
                vehicleId,
                service.findFirstVehicleCategoryId(),
                service.findFirstRegionId(),
                service.findFirstTariffVersionId(),
                110,
                12,
                new BigDecimal("12000.00")
        );
        long applicationId = service.createPolicyApplication(userId, vehicleId, policyId);
        long claimId = service.createClaim(userId, policyId, "CLM-DM-0001", "Test claim");
        long paymentId = service.createPayment(policyId, new BigDecimal("12000.00"), "NEW");
        long notificationId = service.createNotification(userId, "NEW_POLICY_REQUEST", "title", "message");

        assertTrue(userId > 0);
        assertTrue(vehicleId > 0);
        assertTrue(policyId > 0);
        assertTrue(applicationId > 0);
        assertTrue(claimId > 0);
        assertTrue(paymentId > 0);
        assertTrue(notificationId > 0);

        List<?> notifications = service.userNotifications(userId);
        assertEquals(1, notifications.size());
        assertEquals(1, service.markNotificationRead(notificationId));

        Integer claimsCount = jdbc.queryForObject(
                "select count(*) from insurance.claims where user_id = ?",
                Integer.class,
                userId
        );
        assertEquals(1, claimsCount);
    }

    @Test
    void constraintsShouldBeEnforced() {
        service.createUser("dm-user2@test.local");
        assertThrows(DataIntegrityViolationException.class, () -> service.createUser("dm-user2@test.local"));

        assertThrows(DataIntegrityViolationException.class, () ->
                service.createPayment(999999L, new BigDecimal("1.00"), "NEW")
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update(
                        "insert into insurance.claims(number, user_id, status, description, accident_type, contact_phone, " +
                                "consent_personal_data, consent_accuracy, accident_at, accident_place, approved_amount, decided_at) " +
                                "values (?, ?, ?::insurance.claim_status, ?, ?::insurance.accident_type, ?, true, true, now(), ?, ?, ?)",
                        "CLM-BROKEN-1",
                        service.createUser("dm-user3@test.local"),
                        "APPROVED",
                        "broken",
                        "OTHER",
                        "+79001112233",
                        "Moscow",
                        null,
                        null
                )
        );
    }

    @Test
    void transactionShouldRollbackOrCommitConsistently() {
        long userId = service.createUser("dm-user4@test.local");
        long vehicleId = service.createVehicle(userId, "A200AA196", "VIN-DM-0002");
        long policyId = service.createPolicy(
                userId,
                vehicleId,
                service.findFirstVehicleCategoryId(),
                service.findFirstRegionId(),
                service.findFirstTariffVersionId(),
                120,
                12,
                new BigDecimal("13000.00")
        );
        long applicationId = service.createPolicyApplication(userId, vehicleId, policyId);

        assertThrows(IllegalStateException.class, () ->
                service.updatePolicyAndApplicationInTransaction(policyId, "ACTIVE", applicationId, "APPROVED", true)
        );

        String policyStatus = jdbc.queryForObject(
                "select status::text from insurance.policies where id = ?",
                String.class,
                policyId
        );
        String appStatus = jdbc.queryForObject(
                "select status from insurance.policy_applications where id = ?",
                String.class,
                applicationId
        );
        assertEquals("DRAFT", policyStatus);
        assertEquals("NEW", appStatus);

        service.updatePolicyAndApplicationInTransaction(policyId, "ACTIVE", applicationId, "APPROVED", false);
        policyStatus = jdbc.queryForObject(
                "select status::text from insurance.policies where id = ?",
                String.class,
                policyId
        );
        appStatus = jdbc.queryForObject(
                "select status from insurance.policy_applications where id = ?",
                String.class,
                applicationId
        );
        assertEquals("ACTIVE", policyStatus);
        assertEquals("APPROVED", appStatus);
    }

    @Test
    void concurrentStatusUpdateShouldAllowSingleWinner() throws Exception {
        long userId = service.createUser("dm-user5@test.local");
        long vehicleId = service.createVehicle(userId, "A300AA196", "VIN-DM-0003");
        long policyId = service.createPolicy(
                userId,
                vehicleId,
                service.findFirstVehicleCategoryId(),
                service.findFirstRegionId(),
                service.findFirstTariffVersionId(),
                100,
                12,
                new BigDecimal("9000.00")
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = pool.submit(() -> {
            ready.countDown();
            start.await(3, TimeUnit.SECONDS);
            return service.compareAndSetPolicyStatus(policyId, "DRAFT", "ACTIVE");
        });
        Future<Boolean> second = pool.submit(() -> {
            ready.countDown();
            start.await(3, TimeUnit.SECONDS);
            return service.compareAndSetPolicyStatus(policyId, "DRAFT", "CANCELLED");
        });

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();

        boolean firstResult = first.get(3, TimeUnit.SECONDS);
        boolean secondResult = second.get(3, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertNotEquals(firstResult, secondResult);

        String finalStatus = jdbc.queryForObject(
                "select status::text from insurance.policies where id = ?",
                String.class,
                policyId
        );
        assertTrue("ACTIVE".equals(finalStatus) || "CANCELLED".equals(finalStatus));
    }

    @Test
    void shouldBackupDeleteAndRestorePolicy() {
        long userId = service.createUser("dm-user6@test.local");
        long vehicleId = service.createVehicle(userId, "A400AA196", "VIN-DM-0004");
        long policyId = service.createPolicy(
                userId,
                vehicleId,
                service.findFirstVehicleCategoryId(),
                service.findFirstRegionId(),
                service.findFirstTariffVersionId(),
                95,
                6,
                new BigDecimal("7000.00")
        );

        DataManagementService.PolicyBackup backup = service.backupPolicy(policyId);
        assertNotNull(backup);
        assertEquals(userId, backup.userId());
        assertEquals(6, backup.termMonths());

        assertEquals(1, service.deletePolicy(policyId));
        Integer deletedCount = jdbc.queryForObject(
                "select count(*) from insurance.policies where id = ?",
                Integer.class,
                policyId
        );
        assertEquals(0, deletedCount);

        long restoredId = service.restorePolicy(backup);
        assertTrue(restoredId > 0);
        String restoredStatus = jdbc.queryForObject(
                "select status::text from insurance.policies where id = ?",
                String.class,
                restoredId
        );
        LocalDate restoredEndDate = jdbc.queryForObject(
                "select end_date from insurance.policies where id = ?",
                LocalDate.class,
                restoredId
        );
        assertEquals("DRAFT", restoredStatus);
        assertNotNull(restoredEndDate);
    }
}
