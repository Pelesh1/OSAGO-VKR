package vkr.osago.osago;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.agent.AgentAssignmentService;
import vkr.osago.user.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/osago")
public class OsagoController {

    private static final BigDecimal ONE = new BigDecimal("1.0000");
    private static final List<RefKbmClassDto> DEFAULT_KBM_CLASSES = List.of(
            new RefKbmClassDto("3", new BigDecimal("1.1700")),
            new RefKbmClassDto("4", new BigDecimal("1.0000"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;
    private final AgentAssignmentService agentAssignmentService;

    public OsagoController(
            JdbcTemplate jdbcTemplate,
            UserRepository users,
            AgentAssignmentService agentAssignmentService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
        this.agentAssignmentService = agentAssignmentService;
    }

    @GetMapping("/ref-data")
    public RefDataResponse refData() {
        var categories = jdbcTemplate.query(
                """
                select id, code, name
                from insurance.ref_vehicle_categories
                where is_active = true
                order by id
                """,
                (rs, rowNum) -> new RefVehicleCategoryDto(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name")
                )
        );

        var regions = jdbcTemplate.query(
                """
                select id, code, name
                from insurance.ref_regions
                where is_active = true
                order by id
                """,
                (rs, rowNum) -> new RefRegionDto(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name")
                )
        );

        var terms = jdbcTemplate.query(
                """
                select months, name
                from insurance.ref_policy_terms
                where is_active = true
                order by months desc
                """,
                (rs, rowNum) -> new RefTermDto(
                        rs.getInt("months"),
                        rs.getString("name")
                )
        );

        List<RefKbmClassDto> kbmClasses;
        try {
            kbmClasses = jdbcTemplate.query(
                    """
                    select kc.class_code, kc.coefficient
                    from insurance.osago_kbm_coefficients kc
                    join insurance.osago_tariff_versions tv on tv.id = kc.tariff_version_id
                    where tv.is_active = true
                      and tv.valid_from <= current_date
                      and (tv.valid_to is null or tv.valid_to >= current_date)
                    order by kc.coefficient desc, kc.class_code
                    """,
                    (rs, rowNum) -> new RefKbmClassDto(
                            rs.getString("class_code"),
                            rs.getBigDecimal("coefficient")
                    )
            );
            if (kbmClasses.isEmpty()) {
                kbmClasses = DEFAULT_KBM_CLASSES;
            }
        } catch (DataAccessException ex) {
            kbmClasses = DEFAULT_KBM_CLASSES;
        }

        return new RefDataResponse(categories, regions, terms, kbmClasses);
    }

    @PostMapping("/calc")
    public CalcResponse calc(@AuthenticationPrincipal UserDetails principal, @RequestBody CalcRequest req) {
        Long userId = null;
        if (principal != null) {
            userId = users.findByEmail(principal.getUsername()).orElseThrow().getId();
        }
        validateCalcRequest(req);

        String normalizedKbmClass = normalizeKbmClass(req.kbmClassCode());
        Integer driverAgeYears = null;
        Integer driverExperienceYears = null;
        if (!req.unlimitedDrivers()) {
            driverAgeYears = fullYears(req.driverBirthDate(), "driverBirthDate");
            driverExperienceYears = fullYears(req.licenseIssuedDate(), "licenseIssuedDate");
        }
        boolean hasKbmTable = hasTable("insurance.osago_kbm_coefficients");
        boolean hasKvsTable = hasTable("insurance.osago_kvs_coefficients");
        boolean hasExtendedCalcColumns = hasColumn("insurance", "osago_calc_requests", "kbm_class_code");

        String tariffSql = hasKbmTable
                ? """
                select tv.id
                from insurance.osago_tariff_versions tv
                where tv.is_active = true
                  and tv.valid_from <= current_date
                  and (tv.valid_to is null or tv.valid_to >= current_date)
                  and exists (
                      select 1
                      from insurance.osago_base_rates br
                      where br.tariff_version_id = tv.id
                        and br.vehicle_category_id = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_region_coefficients rc
                      where rc.tariff_version_id = tv.id
                        and rc.region_id = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_power_coefficients pc
                      where pc.tariff_version_id = tv.id
                        and pc.hp_from <= ?
                        and (pc.hp_to is null or pc.hp_to >= ?)
                  )
                  and exists (
                      select 1
                      from insurance.osago_term_coefficients tc
                      where tc.tariff_version_id = tv.id
                        and tc.months = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_unlimited_driver_coefficients udc
                      where udc.tariff_version_id = tv.id
                  )
                  and exists (
                      select 1
                      from insurance.osago_kbm_coefficients kbm
                      where kbm.tariff_version_id = tv.id
                        and upper(kbm.class_code) = ?
                  )
                order by tv.valid_from desc, tv.id desc
                limit 1
                """
                : """
                select tv.id
                from insurance.osago_tariff_versions tv
                where tv.is_active = true
                  and tv.valid_from <= current_date
                  and (tv.valid_to is null or tv.valid_to >= current_date)
                  and exists (
                      select 1
                      from insurance.osago_base_rates br
                      where br.tariff_version_id = tv.id
                        and br.vehicle_category_id = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_region_coefficients rc
                      where rc.tariff_version_id = tv.id
                        and rc.region_id = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_power_coefficients pc
                      where pc.tariff_version_id = tv.id
                        and pc.hp_from <= ?
                        and (pc.hp_to is null or pc.hp_to >= ?)
                  )
                  and exists (
                      select 1
                      from insurance.osago_term_coefficients tc
                      where tc.tariff_version_id = tv.id
                        and tc.months = ?
                  )
                  and exists (
                      select 1
                      from insurance.osago_unlimited_driver_coefficients udc
                      where udc.tariff_version_id = tv.id
                  )
                order by tv.valid_from desc, tv.id desc
                limit 1
                """;

        Long tariffVersionId = hasKbmTable
                ? findLong(
                tariffSql,
                req.vehicleCategoryId(),
                req.regionId(),
                req.powerHp(),
                req.powerHp(),
                req.termMonths(),
                normalizedKbmClass
        )
                : findLong(
                tariffSql,
                req.vehicleCategoryId(),
                req.regionId(),
                req.powerHp(),
                req.powerHp(),
                req.termMonths()
        );
        if (tariffVersionId == null) {
            throw new IllegalArgumentException("No active OSAGO tariff found for provided parameters");
        }

        BigDecimal baseRate = findBigDecimal(
                """
                select base_rate
                from insurance.osago_base_rates
                where tariff_version_id = ?
                  and vehicle_category_id = ?
                limit 1
                """,
                tariffVersionId,
                req.vehicleCategoryId()
        );
        if (baseRate == null) {
            throw new IllegalArgumentException("Base rate not found for selected vehicle category");
        }

        BigDecimal coeffRegion = findBigDecimal(
                """
                select coefficient
                from insurance.osago_region_coefficients
                where tariff_version_id = ?
                  and region_id = ?
                limit 1
                """,
                tariffVersionId,
                req.regionId()
        );
        if (coeffRegion == null) {
            throw new IllegalArgumentException("Region coefficient not found");
        }

        BigDecimal coeffPower = findBigDecimal(
                """
                select coefficient
                from insurance.osago_power_coefficients
                where tariff_version_id = ?
                  and hp_from <= ?
                  and (hp_to is null or hp_to >= ?)
                order by hp_from desc
                limit 1
                """,
                tariffVersionId,
                req.powerHp(),
                req.powerHp()
        );
        if (coeffPower == null) {
            throw new IllegalArgumentException("Power coefficient not found");
        }

        BigDecimal coeffDrivers = findBigDecimal(
                """
                select case when ? then coeff_unlimited else coeff_limited end
                from insurance.osago_unlimited_driver_coefficients
                where tariff_version_id = ?
                limit 1
                """,
                req.unlimitedDrivers(),
                tariffVersionId
        );
        if (coeffDrivers == null) {
            coeffDrivers = ONE;
        }

        BigDecimal coeffTerm = findBigDecimal(
                """
                select coefficient
                from insurance.osago_term_coefficients
                where tariff_version_id = ?
                  and months = ?
                limit 1
                """,
                tariffVersionId,
                req.termMonths()
        );
        if (coeffTerm == null) {
            throw new IllegalArgumentException("Insurance term coefficient not found");
        }

        BigDecimal coeffKvs = ONE;
        if (!req.unlimitedDrivers() && hasKvsTable) {
            coeffKvs = findBigDecimal(
                    """
                    select coefficient
                    from insurance.osago_kvs_coefficients
                    where tariff_version_id = ?
                      and age_from <= ?
                      and (age_to is null or age_to >= ?)
                      and exp_from <= ?
                      and (exp_to is null or exp_to >= ?)
                    order by age_from desc, exp_from desc
                    limit 1
                    """,
                    tariffVersionId,
                    driverAgeYears,
                    driverAgeYears,
                    driverExperienceYears,
                    driverExperienceYears
            );
            if (coeffKvs == null) {
                throw new IllegalArgumentException("KVS coefficient not found for provided age and experience");
            }
        }

        BigDecimal coeffKbm = hasKbmTable
                ? findBigDecimal(
                """
                select coefficient
                from insurance.osago_kbm_coefficients
                where tariff_version_id = ?
                  and upper(class_code) = ?
                limit 1
                """,
                tariffVersionId,
                normalizedKbmClass
        )
                : defaultKbm(normalizedKbmClass);
        if (coeffKbm == null) {
            throw new IllegalArgumentException("KBM class is not available in active tariff");
        }

        BigDecimal resultAmount = baseRate
                .multiply(coeffRegion)
                .multiply(coeffPower)
                .multiply(coeffDrivers)
                .multiply(coeffTerm)
                .multiply(coeffKvs)
                .multiply(coeffKbm)
                .setScale(2, RoundingMode.HALF_UP);

        Long calcRequestId;
        if (hasExtendedCalcColumns) {
            calcRequestId = jdbcTemplate.queryForObject(
                    """
                    insert into insurance.osago_calc_requests
                    (user_id, vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, result_amount, tariff_version_id,
                     driver_birth_date, license_issued_date, kbm_class_code, coeff_kvs, coeff_kbm, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    returning id
                    """,
                    Long.class,
                    userId,
                    req.vehicleCategoryId(),
                    req.regionId(),
                    req.powerHp(),
                    req.unlimitedDrivers(),
                    req.termMonths(),
                    resultAmount,
                    tariffVersionId,
                    req.driverBirthDate(),
                    req.licenseIssuedDate(),
                    normalizedKbmClass,
                    coeffKvs,
                    coeffKbm
            );
        } else {
            calcRequestId = jdbcTemplate.queryForObject(
                    """
                    insert into insurance.osago_calc_requests
                    (user_id, vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, result_amount, tariff_version_id, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, now())
                    returning id
                    """,
                    Long.class,
                    userId,
                    req.vehicleCategoryId(),
                    req.regionId(),
                    req.powerHp(),
                    req.unlimitedDrivers(),
                    req.termMonths(),
                    resultAmount,
                    tariffVersionId
            );
        }
        if (calcRequestId == null) {
            throw new IllegalStateException("Failed to persist OSAGO calculation");
        }

        return new CalcResponse(
                calcRequestId,
                tariffVersionId,
                baseRate,
                coeffRegion,
                coeffPower,
                coeffDrivers,
                coeffTerm,
                coeffKvs,
                normalizedKbmClass,
                coeffKbm,
                driverAgeYears,
                driverExperienceYears,
                resultAmount
        );
    }

    @PostMapping("/applications")
    public CreateApplicationResponse createApplication(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody CreateApplicationRequest req
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        validateApplicationRequest(req);

        var calc = jdbcTemplate.query(
                """
                select id, user_id, vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, result_amount, tariff_version_id
                from insurance.osago_calc_requests
                where id = ?
                  and (user_id = ? or user_id is null)
                limit 1
                """,
                (rs, rowNum) -> new CalcRow(
                        rs.getLong("id"),
                        (Long) rs.getObject("user_id"),
                        rs.getLong("vehicle_category_id"),
                        rs.getLong("region_id"),
                        rs.getInt("power_hp"),
                        rs.getBoolean("unlimited_drivers"),
                        rs.getInt("term_months"),
                        rs.getBigDecimal("result_amount"),
                        rs.getLong("tariff_version_id")
                ),
                req.calcRequestId(),
                user.getId()
        );
        if (calc.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calculation not found");
        }
        var calcRow = calc.get(0);

        if (calcRow.userId() == null) {
            jdbcTemplate.update(
                    "update insurance.osago_calc_requests set user_id = ? where id = ? and user_id is null",
                    user.getId(),
                    calcRow.id()
            );
        }

        Long assignedAgentId = agentAssignmentService.ensureAgentAssignedToUser(user.getId());

        Long vehicleId = saveOrReuseVehicle(user.getId(), req.vehicle());
        if (vehicleId == null) {
            throw new IllegalStateException("Failed to save vehicle");
        }

        jdbcTemplate.update(
                """
                insert into insurance.insured_person_profiles
                (user_id, birth_date, passport_series, passport_number, passport_issue_date, passport_issuer, registration_address, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (user_id) do update set
                    birth_date = excluded.birth_date,
                    passport_series = excluded.passport_series,
                    passport_number = excluded.passport_number,
                    passport_issue_date = excluded.passport_issue_date,
                    passport_issuer = excluded.passport_issuer,
                    registration_address = excluded.registration_address,
                    updated_at = now()
                """,
                user.getId(),
                req.insuredPerson().birthDate(),
                trimToNull(req.insuredPerson().passportSeries()),
                trimToNull(req.insuredPerson().passportNumber()),
                req.insuredPerson().passportIssueDate(),
                trimToNull(req.insuredPerson().passportIssuer()),
                trimToNull(req.insuredPerson().registrationAddress())
        );

        jdbcTemplate.update(
                """
                insert into insurance.client_driver_info
                (user_id, driver_license_number, license_issued_date)
                values (?, ?, ?)
                on conflict (user_id) do update set
                    driver_license_number = excluded.driver_license_number,
                    license_issued_date = excluded.license_issued_date
                """,
                user.getId(),
                trimToNull(req.driverInfo().driverLicenseNumber()),
                req.driverInfo().licenseIssuedDate()
        );

        Long policyId = jdbcTemplate.queryForObject(
                """
                insert into insurance.policies
                (number, user_id, type, status, start_date, end_date, created_at, agent_id, vehicle_id, tariff_version_id,
                 vehicle_category_id, region_id, power_hp, unlimited_drivers, term_months, premium_amount, consent_accuracy, consent_personal_data)
                values (null, ?, 'OSAGO'::insurance.policy_type, 'DRAFT'::insurance.policy_status, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                user.getId(),
                req.startDate(),
                req.startDate().plusMonths(calcRow.termMonths()),
                assignedAgentId,
                vehicleId,
                calcRow.tariffVersionId(),
                calcRow.vehicleCategoryId(),
                calcRow.regionId(),
                calcRow.powerHp(),
                calcRow.unlimitedDrivers(),
                calcRow.termMonths(),
                calcRow.resultAmount(),
                req.consentAccuracy(),
                req.consentPersonalData()
        );
        if (policyId == null) {
            throw new IllegalStateException("Failed to create policy draft");
        }

        String policyNumber = generatePolicyNumber(policyId);
        jdbcTemplate.update(
                "update insurance.policies set number = ? where id = ?",
                policyNumber,
                policyId
        );

        Long applicationId = jdbcTemplate.queryForObject(
                """
                insert into insurance.policy_applications
                (user_id, assigned_agent_id, policy_type, vehicle_id, calc_request_id, status, comment, created_at, updated_at, issued_policy_id)
                values (?, ?, 'OSAGO'::insurance.policy_type, ?, ?, 'NEW', null, now(), now(), ?)
                returning id
                """,
                Long.class,
                user.getId(),
                assignedAgentId,
                vehicleId,
                calcRow.id(),
                policyId
        );
        if (applicationId == null) {
            throw new IllegalStateException("Failed to create policy application");
        }

        createNotification(
                user.getId(),
                "NEW_POLICY_REQUEST",
                "Заявка на полис создана",
                "Заявка " + policyNumber + " создана и ожидает проверки агентом."
        );

        return new CreateApplicationResponse(
                applicationId,
                policyId,
                policyNumber,
                "NEW",
                calcRow.resultAmount()
        );
    }

    @GetMapping("/applications/my")
    public List<MyApplicationDto> myApplications(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        purgeExpiredUnpaidApplications(user.getId());
        return jdbcTemplate.query(
                """
                select pa.id, pa.status, pa.created_at, pa.updated_at, pa.issued_policy_id,
                       p.number as policy_number, p.premium_amount, p.status::text as policy_status
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                where pa.user_id = ?
                order by pa.created_at desc
                """,
                (rs, rowNum) -> new MyApplicationDto(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        (Long) rs.getObject("issued_policy_id"),
                        rs.getString("policy_number"),
                        rs.getBigDecimal("premium_amount"),
                        rs.getString("policy_status")
                ),
                user.getId()
        );
    }

    @PostMapping("/applications/{id}/delete-draft")
    public DeleteDraftResponse deleteDraftApplication(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        purgeExpiredUnpaidApplications(user.getId());
        boolean deleted = deleteApplicationAndPolicyIfAllowed(id, user.getId());
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unpaid draft applications can be deleted");
        }
        return new DeleteDraftResponse(id, "DELETED");
    }

    @PostMapping("/applications/{id}/pay")
    public PayResponse payApplication(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody PayRequest req
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        purgeExpiredUnpaidApplications(user.getId());
        if (req == null || req.provider() == null || req.provider().isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }

        var rows = jdbcTemplate.query(
                """
                select pa.id, pa.status, pa.issued_policy_id, p.premium_amount
                from insurance.policy_applications pa
                join insurance.policies p on p.id = pa.issued_policy_id
                where pa.id = ? and pa.user_id = ?
                limit 1
                """,
                (rs, rowNum) -> Map.of(
                        "appStatus", rs.getString("status"),
                        "policyId", rs.getLong("issued_policy_id"),
                        "amount", rs.getBigDecimal("premium_amount")
                ),
                id,
                user.getId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        String appStatus = String.valueOf(rows.get(0).get("appStatus"));
        if (!"APPROVED".equalsIgnoreCase(appStatus) && !"PAYMENT_PENDING".equalsIgnoreCase(appStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not approved by agent");
        }

        Long policyId = (Long) rows.get(0).get("policyId");
        BigDecimal amount = (BigDecimal) rows.get(0).get("amount");

        Long paymentId = jdbcTemplate.queryForObject(
                """
                insert into insurance.payments
                (policy_id, amount, status, provider, external_id, created_at)
                values (?, ?, 'NEW'::insurance.payment_status, ?, ?, now())
                returning id
                """,
                Long.class,
                policyId,
                amount,
                req.provider().trim(),
                UUID.randomUUID().toString()
        );
        if (paymentId == null) {
            throw new IllegalStateException("Failed to create payment");
        }

        jdbcTemplate.update(
                "update insurance.policies set status = 'PENDING_PAY'::insurance.policy_status where id = ?",
                policyId
        );
        jdbcTemplate.update(
                "update insurance.policy_applications set status = 'PAYMENT_PENDING', updated_at = now() where id = ?",
                id
        );

        String paymentUrl = (req.returnUrl() == null || req.returnUrl().isBlank())
                ? "/cabinet/client/index.html"
                : req.returnUrl();

        return new PayResponse(paymentId, "NEW", paymentUrl);
    }

    @PostMapping("/applications/{id}/pay/confirm")
    public PayConfirmResponse confirmPayment(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        purgeExpiredUnpaidApplications(user.getId());

        var rows = jdbcTemplate.query(
                """
                select pa.id as application_id, pa.issued_policy_id as policy_id, p.id as payment_id
                from insurance.policy_applications pa
                left join insurance.payments p on p.policy_id = pa.issued_policy_id
                where pa.id = ? and pa.user_id = ?
                order by p.created_at desc nulls last
                limit 1
                """,
                (rs, rowNum) -> Map.of(
                        "policyId", rs.getLong("policy_id"),
                        "paymentId", rs.getLong("payment_id")
                ),
                id,
                user.getId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        String applicationStatus = jdbcTemplate.queryForObject(
                "select status from insurance.policy_applications where id = ? and user_id = ? limit 1",
                String.class,
                id,
                user.getId()
        );
        if (applicationStatus == null ||
                (!"PAYMENT_PENDING".equalsIgnoreCase(applicationStatus) && !"APPROVED".equalsIgnoreCase(applicationStatus))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not in pending state");
        }

        Long policyId = (Long) rows.get(0).get("policyId");
        Long paymentId = (Long) rows.get(0).get("paymentId");
        if (paymentId == null || paymentId == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment not created");
        }

        jdbcTemplate.update(
                "update insurance.payments set status = 'SUCCESS'::insurance.payment_status where id = ?",
                paymentId
        );
        jdbcTemplate.update(
                "update insurance.policies set status = 'ACTIVE'::insurance.policy_status where id = ?",
                policyId
        );
        jdbcTemplate.update(
                "update insurance.policy_applications set status = 'PAID', updated_at = now() where id = ?",
                id
        );

        createNotification(
                user.getId(),
                "NEW_MESSAGE",
                "Оплата полиса успешна",
                "Полис активирован. Статус: ACTIVE."
        );

        return new PayConfirmResponse(paymentId, "SUCCESS", "ACTIVE");
    }

    private void validateCalcRequest(CalcRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is missing");
        if (req.vehicleCategoryId() == null) throw new IllegalArgumentException("vehicleCategoryId is required");
        if (req.regionId() == null) throw new IllegalArgumentException("regionId is required");
        if (req.powerHp() == null || req.powerHp() <= 0 || req.powerHp() > 2000) {
            throw new IllegalArgumentException("powerHp must be in range 1..2000");
        }
        if (req.termMonths() == null || req.termMonths() <= 0) throw new IllegalArgumentException("termMonths must be > 0");
        if (req.unlimitedDrivers() == null) throw new IllegalArgumentException("unlimitedDrivers is required");
        if (!existsActiveRef("ref_vehicle_categories", "id", req.vehicleCategoryId())) {
            throw new IllegalArgumentException("vehicleCategoryId is invalid");
        }
        if (!existsActiveRef("ref_regions", "id", req.regionId())) {
            throw new IllegalArgumentException("regionId is invalid");
        }
        if (!existsActiveRef("ref_policy_terms", "months", req.termMonths())) {
            throw new IllegalArgumentException("termMonths is invalid");
        }
        if (!req.unlimitedDrivers()) {
            if (req.driverBirthDate() == null) throw new IllegalArgumentException("driverBirthDate is required for limited drivers");
            if (req.licenseIssuedDate() == null) throw new IllegalArgumentException("licenseIssuedDate is required for limited drivers");
            if (req.driverBirthDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("driverBirthDate cannot be in the future");
            }
            if (req.licenseIssuedDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("licenseIssuedDate cannot be in the future");
            }
        }
    }

    private void validateApplicationRequest(CreateApplicationRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is missing");
        if (req.calcRequestId() == null) throw new IllegalArgumentException("calcRequestId is required");
        if (req.vehicle() == null) throw new IllegalArgumentException("vehicle is required");
        if (req.insuredPerson() == null) throw new IllegalArgumentException("insuredPerson is required");
        if (req.driverInfo() == null) throw new IllegalArgumentException("driverInfo is required");
        if (req.startDate() == null) throw new IllegalArgumentException("startDate is required");
        if (req.consentAccuracy() == null || !req.consentAccuracy()) {
            throw new IllegalArgumentException("consentAccuracy must be accepted");
        }
        if (req.consentPersonalData() == null || !req.consentPersonalData()) {
            throw new IllegalArgumentException("consentPersonalData must be accepted");
        }
        if (req.vehicle().brand() == null || req.vehicle().brand().isBlank()) {
            throw new IllegalArgumentException("brand is required");
        }
        if (req.vehicle().brand().trim().length() < 2 || req.vehicle().brand().trim().length() > 100) {
            throw new IllegalArgumentException("brand has invalid length");
        }
        String vin = trimToNull(req.vehicle().vin());
        String reg = trimToNull(req.vehicle().regNumber());
        if (vin == null && reg == null) {
            throw new IllegalArgumentException("either vin or regNumber is required");
        }
        if (vin != null && !vin.toUpperCase().matches("^[A-HJ-NPR-Z0-9]{11,17}$")) {
            throw new IllegalArgumentException("vin has invalid format");
        }
        if (reg != null) {
            String normalizedReg = reg.toUpperCase().replaceAll("\\s+", "");
            if (!normalizedReg.matches("^[АВЕКМНОРСТУХABEKMHOPCTYX][0-9]{3}[АВЕКМНОРСТУХABEKMHOPCTYX]{2}[0-9]{2,3}$")) {
                throw new IllegalArgumentException("regNumber has invalid format");
            }
        }
        if (req.insuredPerson().birthDate() == null) {
            throw new IllegalArgumentException("birthDate is required");
        }
        if (req.insuredPerson().birthDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("birthDate cannot be in the future");
        }
        if (Period.between(req.insuredPerson().birthDate(), LocalDate.now()).getYears() < 18) {
            throw new IllegalArgumentException("insured person must be 18+");
        }
        if (req.insuredPerson().passportSeries() == null || req.insuredPerson().passportSeries().isBlank()) {
            throw new IllegalArgumentException("passportSeries is required");
        }
        if (req.insuredPerson().passportNumber() == null || req.insuredPerson().passportNumber().isBlank()) {
            throw new IllegalArgumentException("passportNumber is required");
        }
        String passportSeriesDigits = digitsOnly(req.insuredPerson().passportSeries());
        String passportNumberDigits = digitsOnly(req.insuredPerson().passportNumber());
        if (passportSeriesDigits.length() != 4) {
            throw new IllegalArgumentException("passportSeries must contain 4 digits");
        }
        if (passportNumberDigits.length() != 6) {
            throw new IllegalArgumentException("passportNumber must contain 6 digits");
        }
        if (req.insuredPerson().passportIssueDate() == null) {
            throw new IllegalArgumentException("passportIssueDate is required");
        }
        if (req.insuredPerson().passportIssueDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("passportIssueDate cannot be in the future");
        }
        if (req.insuredPerson().passportIssuer() == null || req.insuredPerson().passportIssuer().isBlank()) {
            throw new IllegalArgumentException("passportIssuer is required");
        }
        if (req.insuredPerson().registrationAddress() == null || req.insuredPerson().registrationAddress().isBlank()) {
            throw new IllegalArgumentException("registrationAddress is required");
        }
        if (req.driverInfo().driverLicenseNumber() == null || req.driverInfo().driverLicenseNumber().isBlank()) {
            throw new IllegalArgumentException("driverLicenseNumber is required");
        }
        if (digitsOnly(req.driverInfo().driverLicenseNumber()).length() < 6) {
            throw new IllegalArgumentException("driverLicenseNumber has invalid format");
        }
        if (req.driverInfo().licenseIssuedDate() == null) {
            throw new IllegalArgumentException("licenseIssuedDate is required");
        }
        if (req.driverInfo().licenseIssuedDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("licenseIssuedDate cannot be in the future");
        }
        if (req.startDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("startDate cannot be in the past");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private boolean existsActiveRef(String tableName, String idColumn, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from insurance." + tableName + " where " + idColumn + " = ? and is_active = true",
                Integer.class,
                value
        );
        return count != null && count > 0;
    }

    private Long saveOrReuseVehicle(Long userId, VehicleDto vehicle) {
        String brand = vehicle.brand().trim();
        String model = trimToNull(vehicle.model());
        String vin = trimToNull(vehicle.vin());
        String regNumber = trimToNull(vehicle.regNumber());

        try {
            return jdbcTemplate.queryForObject(
                    """
                    insert into insurance.vehicles
                    (owner_user_id, brand, model, vin, reg_number, created_at)
                    values (?, ?, ?, ?, ?, now())
                    returning id
                    """,
                    Long.class,
                    userId,
                    brand,
                    model,
                    vin,
                    regNumber
            );
        } catch (DataAccessException ex) {
            var existing = jdbcTemplate.query(
                    """
                    select id, owner_user_id
                    from insurance.vehicles
                    where (? is not null and reg_number = ?)
                       or (? is not null and vin = ?)
                    order by id desc
                    limit 1
                    """,
                    (rs, rowNum) -> Map.of(
                            "id", rs.getLong("id"),
                            "ownerUserId", rs.getLong("owner_user_id")
                    ),
                    regNumber,
                    regNumber,
                    vin,
                    vin
            );
            if (existing.isEmpty()) {
                throw ex;
            }

            Long existingId = (Long) existing.get(0).get("id");
            Long ownerUserId = (Long) existing.get(0).get("ownerUserId");
            if (!userId.equals(ownerUserId)) {
                throw new IllegalArgumentException("Автомобиль с таким VIN/госномером уже привязан к другому пользователю");
            }

            jdbcTemplate.update(
                    """
                    update insurance.vehicles
                    set brand = ?,
                        model = ?,
                        vin = ?,
                        reg_number = ?
                    where id = ?
                    """,
                    brand,
                    model,
                    vin,
                    regNumber,
                    existingId
            );
            return existingId;
        }
    }

    private Long findLong(String sql, Object... args) {
        var rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Integer fullYears(LocalDate fromDate, String fieldName) {
        if (fromDate == null) {
            return null;
        }
        var now = LocalDate.now();
        if (fromDate.isAfter(now)) {
            throw new IllegalArgumentException(fieldName + " cannot be in the future");
        }
        int years = Period.between(fromDate, now).getYears();
        if (years < 0) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return years;
    }

    private String normalizeKbmClass(String value) {
        if (value == null || value.isBlank()) {
            return "3";
        }
        return value.trim().toUpperCase();
    }

    private BigDecimal findBigDecimal(String sql, Object... args) {
        var rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean hasTable(String qualifiedName) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null",
                    Boolean.class,
                    qualifiedName
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private boolean hasColumn(String schema, String table, String column) {
        try {
            Integer found = jdbcTemplate.queryForObject(
                    """
                    select count(*)
                    from information_schema.columns
                    where table_schema = ?
                      and table_name = ?
                      and column_name = ?
                    """,
                    Integer.class,
                    schema,
                    table,
                    column
            );
            return found != null && found > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private BigDecimal defaultKbm(String classCode) {
        if ("M".equalsIgnoreCase(classCode)) return new BigDecimal("3.9200");
        if ("0".equals(classCode)) return new BigDecimal("2.9400");
        if ("1".equals(classCode)) return new BigDecimal("2.2500");
        if ("2".equals(classCode)) return new BigDecimal("1.7600");
        if ("3".equals(classCode)) return new BigDecimal("1.1700");
        if ("4".equals(classCode)) return new BigDecimal("1.0000");
        if ("5".equals(classCode)) return new BigDecimal("0.9100");
        if ("6".equals(classCode)) return new BigDecimal("0.8300");
        if ("7".equals(classCode)) return new BigDecimal("0.7800");
        if ("8".equals(classCode)) return new BigDecimal("0.7400");
        if ("9".equals(classCode)) return new BigDecimal("0.6800");
        if ("10".equals(classCode)) return new BigDecimal("0.6300");
        if ("11".equals(classCode)) return new BigDecimal("0.5700");
        if ("12".equals(classCode)) return new BigDecimal("0.5200");
        if ("13".equals(classCode)) return new BigDecimal("0.4600");
        return new BigDecimal("1.1700");
    }

    private void createNotification(Long recipientId, String type, String title, String message) {
        jdbcTemplate.update(
                """
                insert into insurance.notifications
                (recipient_id, type, title, message, is_read, created_at)
                values (?, ?, ?, ?, false, now())
                """,
                recipientId,
                type,
                title,
                message
        );
    }

    private void purgeExpiredUnpaidApplications(Long userId) {
        var stale = jdbcTemplate.query(
                """
                select pa.id, pa.issued_policy_id
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                where pa.user_id = ?
                  and upper(pa.status) in ('APPROVED', 'PAYMENT_PENDING')
                  and pa.updated_at < (now() - interval '1 day')
                  and (
                        p.id is null
                        or p.status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                  )
                """,
                (rs, rowNum) -> Map.of(
                        "applicationId", rs.getLong("id"),
                        "policyId", (Long) rs.getObject("issued_policy_id")
                ),
                userId
        );

        for (var row : stale) {
            Long applicationId = (Long) row.get("applicationId");
            Long policyId = (Long) row.get("policyId");
            jdbcTemplate.update(
                    "delete from insurance.policy_applications where id = ? and user_id = ?",
                    applicationId,
                    userId
            );
            if (policyId != null) {
                jdbcTemplate.update(
                        """
                        delete from insurance.policies
                        where id = ?
                          and user_id = ?
                          and status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                        """,
                        policyId,
                        userId
                );
            }
        }
    }

    private boolean deleteApplicationAndPolicyIfAllowed(Long applicationId, Long userId) {
        var rows = jdbcTemplate.query(
                """
                select pa.id, pa.status, pa.issued_policy_id, p.status::text as policy_status
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                where pa.id = ? and pa.user_id = ?
                limit 1
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "status", rs.getString("status"),
                        "policyId", (Long) rs.getObject("issued_policy_id"),
                        "policyStatus", rs.getString("policy_status")
                ),
                applicationId,
                userId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        String appStatus = String.valueOf(rows.get(0).get("status")).toUpperCase();
        Long policyId = (Long) rows.get(0).get("policyId");
        String policyStatus = rows.get(0).get("policyStatus") == null
                ? null
                : String.valueOf(rows.get(0).get("policyStatus")).toUpperCase();

        boolean allowedByStatus = "NEW".equals(appStatus)
                || "IN_REVIEW".equals(appStatus)
                || "NEED_INFO".equals(appStatus)
                || "APPROVED".equals(appStatus)
                || "PAYMENT_PENDING".equals(appStatus);
        boolean allowedByPolicy = policyStatus == null
                || "DRAFT".equals(policyStatus)
                || "PENDING_PAY".equals(policyStatus);
        if (!allowedByStatus || !allowedByPolicy) {
            return false;
        }

        jdbcTemplate.update(
                "delete from insurance.policy_applications where id = ? and user_id = ?",
                applicationId,
                userId
        );
        if (policyId != null) {
            jdbcTemplate.update(
                    """
                    delete from insurance.policies
                    where id = ?
                      and user_id = ?
                      and status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                    """,
                    policyId,
                    userId
            );
        }
        return true;
    }

    private String generatePolicyNumber(Long id) {
        return String.format("EEE %09d", id);
    }

    public record RefDataResponse(
            List<RefVehicleCategoryDto> vehicleCategories,
            List<RefRegionDto> regions,
            List<RefTermDto> terms,
            List<RefKbmClassDto> kbmClasses
    ) {
    }

    public record RefVehicleCategoryDto(Long id, String code, String name) {
    }

    public record RefRegionDto(Long id, String code, String name) {
    }

    public record RefTermDto(Integer months, String name) {
    }

    public record RefKbmClassDto(String code, BigDecimal coefficient) {
    }

    public record CalcRequest(
            Long vehicleCategoryId,
            Long regionId,
            Integer powerHp,
            Boolean unlimitedDrivers,
            Integer termMonths,
            LocalDate driverBirthDate,
            LocalDate licenseIssuedDate,
            String kbmClassCode
    ) {
    }

    public record CalcResponse(
            Long calcRequestId,
            Long tariffVersionId,
            BigDecimal baseRate,
            BigDecimal coeffRegion,
            BigDecimal coeffPower,
            BigDecimal coeffDrivers,
            BigDecimal coeffTerm,
            BigDecimal coeffKvs,
            String kbmClassCode,
            BigDecimal coeffKbm,
            Integer driverAgeYears,
            Integer driverExperienceYears,
            BigDecimal resultAmount
    ) {
    }

    public record CreateApplicationRequest(
            Long calcRequestId,
            VehicleDto vehicle,
            InsuredPersonDto insuredPerson,
            DriverInfoDto driverInfo,
            LocalDate startDate,
            Boolean consentAccuracy,
            Boolean consentPersonalData
    ) {
    }

    public record VehicleDto(
            String brand,
            String model,
            String vin,
            String regNumber
    ) {
    }

    public record InsuredPersonDto(
            LocalDate birthDate,
            String passportSeries,
            String passportNumber,
            LocalDate passportIssueDate,
            String passportIssuer,
            String registrationAddress
    ) {
    }

    public record DriverInfoDto(
            String driverLicenseNumber,
            LocalDate licenseIssuedDate
    ) {
    }

    public record CreateApplicationResponse(
            Long applicationId,
            Long policyId,
            String policyNumber,
            String status,
            BigDecimal premiumAmount
    ) {
    }

    public record MyApplicationDto(
            Long id,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long policyId,
            String policyNumber,
            BigDecimal premiumAmount,
            String policyStatus
    ) {
    }

    public record DeleteDraftResponse(
            Long id,
            String status
    ) {
    }

    public record PayRequest(
            String provider,
            String returnUrl
    ) {
    }

    public record PayResponse(
            Long paymentId,
            String status,
            String paymentUrl
    ) {
    }

    public record PayConfirmResponse(
            Long paymentId,
            String paymentStatus,
            String policyStatus
    ) {
    }

    private record CalcRow(
            Long id,
            Long userId,
            Long vehicleCategoryId,
            Long regionId,
            Integer powerHp,
            Boolean unlimitedDrivers,
            Integer termMonths,
            BigDecimal resultAmount,
            Long tariffVersionId
    ) {
    }
}


