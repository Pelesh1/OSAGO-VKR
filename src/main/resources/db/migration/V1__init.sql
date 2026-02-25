--
-- PostgreSQL database dump
--




--
-- Name: insurance; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA insurance;


--
-- Name: accident_type; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.accident_type AS ENUM (
    'COLLISION',
    'TREE_FALL',
    'OBSTACLE_HIT',
    'THEFT',
    'OTHER'
);


--
-- Name: attachment_type; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.attachment_type AS ENUM (
    'DAMAGE_PHOTO',
    'ACCIDENT_DOC'
);


--
-- Name: claim_status; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.claim_status AS ENUM (
    'NEW',
    'IN_REVIEW',
    'NEED_INFO',
    'APPROVED',
    'REJECTED',
    'CLOSED'
);


--
-- Name: notification_type; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.notification_type AS ENUM (
    'NEW_POLICY_REQUEST',
    'CLAIM_NEEDS_ATTENTION',
    'NEW_MESSAGE'
);


--
-- Name: payment_status; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.payment_status AS ENUM (
    'NEW',
    'SUCCESS',
    'FAIL'
);


--
-- Name: policy_status; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.policy_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'EXPIRED',
    'CANCELLED',
    'PENDING_PAY'
);


--
-- Name: policy_type; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.policy_type AS ENUM (
    'OSAGO',
    'KASKO'
);


--
-- Name: report_export_format; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.report_export_format AS ENUM (
    'PDF',
    'XLSX'
);


--
-- Name: report_metric; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.report_metric AS ENUM (
    'TOTAL_POLICIES',
    'OSAGO_POLICIES',
    'KASKO_POLICIES',
    'AVG_POLICY_PRICE',
    'TOTAL_PREMIUM_SUM'
);


--
-- Name: report_type; Type: TYPE; Schema: insurance; Owner: -
--

CREATE TYPE insurance.report_type AS ENUM (
    'POLICIES_SUMMARY'
);


--
-- Name: gen_policy_number(); Type: FUNCTION; Schema: insurance; Owner: -
--

CREATE FUNCTION insurance.gen_policy_number() RETURNS character varying
    LANGUAGE plpgsql
    AS $$
DECLARE
  n BIGINT;
BEGIN
  n := nextval('insurance.policy_number_seq');
  RETURN 'EEE ' || lpad(n::text, 9, '0');
END;
$$;


--
-- Name: policies_set_number(); Type: FUNCTION; Schema: insurance; Owner: -
--

CREATE FUNCTION insurance.policies_set_number() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.number IS NULL OR NEW.number = '' THEN
    NEW.number := insurance.gen_policy_number();
  END IF;
  RETURN NEW;
END;
$$;


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: insurance; Owner: -
--

CREATE FUNCTION insurance.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agent_profiles; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.agent_profiles (
    user_id bigint NOT NULL,
    phone character varying(32),
    work_email character varying(255)
);


--
-- Name: application_attachments; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.application_attachments (
    id bigint NOT NULL,
    application_id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    content_type character varying(128),
    storage_key character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    attachment_type character varying(50) DEFAULT 'DOC'::character varying NOT NULL
);


--
-- Name: application_attachments_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.application_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: application_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.application_attachments_id_seq OWNED BY insurance.application_attachments.id;


--
-- Name: chat_messages; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.chat_messages (
    id bigint NOT NULL,
    chat_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    message_text text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_messages_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.chat_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.chat_messages_id_seq OWNED BY insurance.chat_messages.id;


--
-- Name: chats; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.chats (
    id bigint NOT NULL,
    client_id bigint NOT NULL,
    agent_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chats_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.chats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chats_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.chats_id_seq OWNED BY insurance.chats.id;


--
-- Name: claim_attachments; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.claim_attachments (
    id bigint NOT NULL,
    claim_id bigint NOT NULL,
    file_name character varying(255) NOT NULL,
    content_type character varying(128),
    storage_key character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    attachment_type insurance.attachment_type DEFAULT 'ACCIDENT_DOC'::insurance.attachment_type NOT NULL
);


--
-- Name: claim_attachments_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.claim_attachments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: claim_attachments_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.claim_attachments_id_seq OWNED BY insurance.claim_attachments.id;


--
-- Name: claims; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.claims (
    id bigint NOT NULL,
    number character varying(64),
    user_id bigint NOT NULL,
    policy_id bigint,
    status insurance.claim_status DEFAULT 'NEW'::insurance.claim_status NOT NULL,
    description text,
    assigned_agent_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    accident_type insurance.accident_type DEFAULT 'OTHER'::insurance.accident_type NOT NULL,
    contact_phone character varying(32) DEFAULT '+7'::character varying NOT NULL,
    contact_email character varying(255),
    consent_personal_data boolean DEFAULT false NOT NULL,
    consent_accuracy boolean DEFAULT false NOT NULL,
    accident_at timestamp with time zone DEFAULT now() NOT NULL,
    accident_place character varying(500) DEFAULT ''::character varying NOT NULL,
    approved_amount numeric(12,2),
    decision_comment text,
    decided_at timestamp with time zone,
    paid_at timestamp with time zone,
    CONSTRAINT chk_claim_approved_requires_amount CHECK (((status <> 'APPROVED'::insurance.claim_status) OR ((approved_amount IS NOT NULL) AND (decided_at IS NOT NULL)))),
    CONSTRAINT chk_claim_closed_requires_decision CHECK (((status <> 'CLOSED'::insurance.claim_status) OR ((decided_at IS NOT NULL) AND ((approved_amount IS NOT NULL) OR (decision_comment IS NOT NULL))))),
    CONSTRAINT chk_claim_rejected_requires_comment CHECK (((status <> 'REJECTED'::insurance.claim_status) OR ((decision_comment IS NOT NULL) AND (decided_at IS NOT NULL))))
);


--
-- Name: claims_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.claims_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: claims_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.claims_id_seq OWNED BY insurance.claims.id;


--
-- Name: client_driver_info; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.client_driver_info (
    user_id bigint NOT NULL,
    driver_license_number character varying(32),
    license_issued_date date
);


--
-- Name: insured_person_profiles; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.insured_person_profiles (
    user_id bigint NOT NULL,
    birth_date date,
    passport_series character varying(10),
    passport_number character varying(20),
    passport_issue_date date,
    passport_issuer character varying(255),
    registration_address character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.notifications (
    id bigint NOT NULL,
    recipient_id bigint CONSTRAINT notifications_user_id_not_null NOT NULL,
    type character varying(50) NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    read_at timestamp with time zone,
    body text
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.notifications_id_seq OWNED BY insurance.notifications.id;


--
-- Name: osago_base_rates; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_base_rates (
    id bigint NOT NULL,
    tariff_version_id bigint NOT NULL,
    vehicle_category_id bigint NOT NULL,
    base_rate numeric(12,2) NOT NULL
);


--
-- Name: osago_base_rates_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_base_rates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_base_rates_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_base_rates_id_seq OWNED BY insurance.osago_base_rates.id;


--
-- Name: osago_calc_requests; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_calc_requests (
    id bigint NOT NULL,
    user_id bigint,
    vehicle_category_id bigint NOT NULL,
    region_id bigint NOT NULL,
    power_hp integer NOT NULL,
    unlimited_drivers boolean NOT NULL,
    term_months integer NOT NULL,
    result_amount numeric(12,2) NOT NULL,
    tariff_version_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: osago_calc_requests_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_calc_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_calc_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_calc_requests_id_seq OWNED BY insurance.osago_calc_requests.id;


--
-- Name: osago_power_coefficients; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_power_coefficients (
    id bigint NOT NULL,
    tariff_version_id bigint NOT NULL,
    hp_from integer NOT NULL,
    hp_to integer,
    coefficient numeric(8,4) NOT NULL,
    CONSTRAINT osago_power_coefficients_check CHECK (((hp_to IS NULL) OR (hp_to >= hp_from))),
    CONSTRAINT osago_power_coefficients_hp_from_check CHECK ((hp_from >= 0))
);


--
-- Name: osago_power_coefficients_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_power_coefficients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_power_coefficients_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_power_coefficients_id_seq OWNED BY insurance.osago_power_coefficients.id;


--
-- Name: osago_region_coefficients; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_region_coefficients (
    id bigint NOT NULL,
    tariff_version_id bigint NOT NULL,
    region_id bigint NOT NULL,
    coefficient numeric(8,4) NOT NULL
);


--
-- Name: osago_region_coefficients_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_region_coefficients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_region_coefficients_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_region_coefficients_id_seq OWNED BY insurance.osago_region_coefficients.id;


--
-- Name: osago_tariff_versions; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_tariff_versions (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    valid_from date NOT NULL,
    valid_to date,
    is_active boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: osago_tariff_versions_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_tariff_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_tariff_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_tariff_versions_id_seq OWNED BY insurance.osago_tariff_versions.id;


--
-- Name: osago_term_coefficients; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_term_coefficients (
    id bigint NOT NULL,
    tariff_version_id bigint NOT NULL,
    months integer NOT NULL,
    coefficient numeric(8,4) NOT NULL
);


--
-- Name: osago_term_coefficients_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.osago_term_coefficients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: osago_term_coefficients_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.osago_term_coefficients_id_seq OWNED BY insurance.osago_term_coefficients.id;


--
-- Name: osago_unlimited_driver_coefficients; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.osago_unlimited_driver_coefficients (
    tariff_version_id bigint NOT NULL,
    coeff_limited numeric(8,4) DEFAULT 1.0000 NOT NULL,
    coeff_unlimited numeric(8,4) NOT NULL
);


--
-- Name: payments; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.payments (
    id bigint NOT NULL,
    policy_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    status insurance.payment_status DEFAULT 'NEW'::insurance.payment_status NOT NULL,
    provider character varying(50),
    external_id character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.payments_id_seq OWNED BY insurance.payments.id;


--
-- Name: policies; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.policies (
    id bigint NOT NULL,
    number character varying(64),
    user_id bigint NOT NULL,
    type insurance.policy_type NOT NULL,
    status insurance.policy_status DEFAULT 'DRAFT'::insurance.policy_status NOT NULL,
    start_date date,
    end_date date,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    agent_id bigint,
    vehicle_id bigint,
    tariff_version_id bigint,
    vehicle_category_id bigint,
    region_id bigint,
    power_hp integer,
    unlimited_drivers boolean DEFAULT false NOT NULL,
    term_months integer,
    premium_amount numeric(12,2) DEFAULT 0 NOT NULL,
    consent_accuracy boolean DEFAULT false NOT NULL,
    consent_personal_data boolean DEFAULT false NOT NULL
);


--
-- Name: policies_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: policies_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.policies_id_seq OWNED BY insurance.policies.id;


--
-- Name: policy_applications; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.policy_applications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    assigned_agent_id bigint,
    policy_type insurance.policy_type NOT NULL,
    vehicle_id bigint,
    calc_request_id bigint,
    status character varying(30) DEFAULT 'NEW'::character varying NOT NULL,
    comment text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    issued_policy_id bigint
);


--
-- Name: policy_applications_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.policy_applications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: policy_applications_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.policy_applications_id_seq OWNED BY insurance.policy_applications.id;


--
-- Name: policy_drivers; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.policy_drivers (
    policy_id bigint NOT NULL,
    user_id bigint NOT NULL,
    added_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: policy_number_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.policy_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 50;


--
-- Name: policy_versions; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.policy_versions (
    id bigint NOT NULL,
    policy_id bigint NOT NULL,
    version_no integer NOT NULL,
    snapshot jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint
);


--
-- Name: policy_versions_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.policy_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: policy_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.policy_versions_id_seq OWNED BY insurance.policy_versions.id;


--
-- Name: ref_policy_terms; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.ref_policy_terms (
    months integer NOT NULL,
    name character varying(100) NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: ref_regions; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.ref_regions (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: ref_regions_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.ref_regions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ref_regions_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.ref_regions_id_seq OWNED BY insurance.ref_regions.id;


--
-- Name: ref_vehicle_categories; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.ref_vehicle_categories (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


--
-- Name: ref_vehicle_categories_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.ref_vehicle_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ref_vehicle_categories_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.ref_vehicle_categories_id_seq OWNED BY insurance.ref_vehicle_categories.id;


--
-- Name: report_exports; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.report_exports (
    id bigint NOT NULL,
    report_id bigint NOT NULL,
    format insurance.report_export_format NOT NULL,
    storage_key character varying(512) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: report_exports_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.report_exports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_exports_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.report_exports_id_seq OWNED BY insurance.report_exports.id;


--
-- Name: report_rows; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.report_rows (
    id bigint NOT NULL,
    report_id bigint NOT NULL,
    metric insurance.report_metric NOT NULL,
    quantity bigint,
    amount numeric(14,2),
    note character varying(255)
);


--
-- Name: report_rows_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.report_rows_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_rows_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.report_rows_id_seq OWNED BY insurance.report_rows.id;


--
-- Name: reports; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.reports (
    id bigint NOT NULL,
    created_by bigint NOT NULL,
    name character varying(255) NOT NULL,
    report_type insurance.report_type NOT NULL,
    period_from date NOT NULL,
    period_to date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reports_check CHECK ((period_to >= period_from))
);


--
-- Name: reports_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reports_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.reports_id_seq OWNED BY insurance.reports.id;


--
-- Name: roles; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.roles (
    id bigint NOT NULL,
    code character varying(50) NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.roles_id_seq OWNED BY insurance.roles.id;


--
-- Name: user_roles; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    middle_name character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    self_registered boolean DEFAULT true NOT NULL,
    created_by_user_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    assigned_agent_id bigint
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.users_id_seq OWNED BY insurance.users.id;


--
-- Name: vehicles; Type: TABLE; Schema: insurance; Owner: -
--

CREATE TABLE insurance.vehicles (
    id bigint NOT NULL,
    owner_user_id bigint NOT NULL,
    brand character varying(80) NOT NULL,
    model character varying(80),
    vin character varying(32),
    reg_number character varying(32),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: vehicles_id_seq; Type: SEQUENCE; Schema: insurance; Owner: -
--

CREATE SEQUENCE insurance.vehicles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehicles_id_seq; Type: SEQUENCE OWNED BY; Schema: insurance; Owner: -
--

ALTER SEQUENCE insurance.vehicles_id_seq OWNED BY insurance.vehicles.id;


--
-- Name: application_attachments id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.application_attachments ALTER COLUMN id SET DEFAULT nextval('insurance.application_attachments_id_seq'::regclass);


--
-- Name: chat_messages id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chat_messages ALTER COLUMN id SET DEFAULT nextval('insurance.chat_messages_id_seq'::regclass);


--
-- Name: chats id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chats ALTER COLUMN id SET DEFAULT nextval('insurance.chats_id_seq'::regclass);


--
-- Name: claim_attachments id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claim_attachments ALTER COLUMN id SET DEFAULT nextval('insurance.claim_attachments_id_seq'::regclass);


--
-- Name: claims id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims ALTER COLUMN id SET DEFAULT nextval('insurance.claims_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.notifications ALTER COLUMN id SET DEFAULT nextval('insurance.notifications_id_seq'::regclass);


--
-- Name: osago_base_rates id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_base_rates ALTER COLUMN id SET DEFAULT nextval('insurance.osago_base_rates_id_seq'::regclass);


--
-- Name: osago_calc_requests id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests ALTER COLUMN id SET DEFAULT nextval('insurance.osago_calc_requests_id_seq'::regclass);


--
-- Name: osago_power_coefficients id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_power_coefficients ALTER COLUMN id SET DEFAULT nextval('insurance.osago_power_coefficients_id_seq'::regclass);


--
-- Name: osago_region_coefficients id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_region_coefficients ALTER COLUMN id SET DEFAULT nextval('insurance.osago_region_coefficients_id_seq'::regclass);


--
-- Name: osago_tariff_versions id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_tariff_versions ALTER COLUMN id SET DEFAULT nextval('insurance.osago_tariff_versions_id_seq'::regclass);


--
-- Name: osago_term_coefficients id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_term_coefficients ALTER COLUMN id SET DEFAULT nextval('insurance.osago_term_coefficients_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.payments ALTER COLUMN id SET DEFAULT nextval('insurance.payments_id_seq'::regclass);


--
-- Name: policies id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies ALTER COLUMN id SET DEFAULT nextval('insurance.policies_id_seq'::regclass);


--
-- Name: policy_applications id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications ALTER COLUMN id SET DEFAULT nextval('insurance.policy_applications_id_seq'::regclass);


--
-- Name: policy_versions id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_versions ALTER COLUMN id SET DEFAULT nextval('insurance.policy_versions_id_seq'::regclass);


--
-- Name: ref_regions id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_regions ALTER COLUMN id SET DEFAULT nextval('insurance.ref_regions_id_seq'::regclass);


--
-- Name: ref_vehicle_categories id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_vehicle_categories ALTER COLUMN id SET DEFAULT nextval('insurance.ref_vehicle_categories_id_seq'::regclass);


--
-- Name: report_exports id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_exports ALTER COLUMN id SET DEFAULT nextval('insurance.report_exports_id_seq'::regclass);


--
-- Name: report_rows id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_rows ALTER COLUMN id SET DEFAULT nextval('insurance.report_rows_id_seq'::regclass);


--
-- Name: reports id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.reports ALTER COLUMN id SET DEFAULT nextval('insurance.reports_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.roles ALTER COLUMN id SET DEFAULT nextval('insurance.roles_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.users ALTER COLUMN id SET DEFAULT nextval('insurance.users_id_seq'::regclass);


--
-- Name: vehicles id; Type: DEFAULT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.vehicles ALTER COLUMN id SET DEFAULT nextval('insurance.vehicles_id_seq'::regclass);


--
-- Name: agent_profiles agent_profiles_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.agent_profiles
    ADD CONSTRAINT agent_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: application_attachments application_attachments_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.application_attachments
    ADD CONSTRAINT application_attachments_pkey PRIMARY KEY (id);


--
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);


--
-- Name: chats chats_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chats
    ADD CONSTRAINT chats_pkey PRIMARY KEY (id);


--
-- Name: claim_attachments claim_attachments_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claim_attachments
    ADD CONSTRAINT claim_attachments_pkey PRIMARY KEY (id);


--
-- Name: claims claims_number_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims
    ADD CONSTRAINT claims_number_key UNIQUE (number);


--
-- Name: claims claims_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims
    ADD CONSTRAINT claims_pkey PRIMARY KEY (id);


--
-- Name: client_driver_info client_driver_info_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.client_driver_info
    ADD CONSTRAINT client_driver_info_pkey PRIMARY KEY (user_id);


--
-- Name: insured_person_profiles insured_person_profiles_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.insured_person_profiles
    ADD CONSTRAINT insured_person_profiles_pkey PRIMARY KEY (user_id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: osago_base_rates osago_base_rates_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_base_rates
    ADD CONSTRAINT osago_base_rates_pkey PRIMARY KEY (id);


--
-- Name: osago_base_rates osago_base_rates_tariff_version_id_vehicle_category_id_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_base_rates
    ADD CONSTRAINT osago_base_rates_tariff_version_id_vehicle_category_id_key UNIQUE (tariff_version_id, vehicle_category_id);


--
-- Name: osago_calc_requests osago_calc_requests_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_pkey PRIMARY KEY (id);


--
-- Name: osago_power_coefficients osago_power_coefficients_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_power_coefficients
    ADD CONSTRAINT osago_power_coefficients_pkey PRIMARY KEY (id);


--
-- Name: osago_region_coefficients osago_region_coefficients_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_region_coefficients
    ADD CONSTRAINT osago_region_coefficients_pkey PRIMARY KEY (id);


--
-- Name: osago_region_coefficients osago_region_coefficients_tariff_version_id_region_id_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_region_coefficients
    ADD CONSTRAINT osago_region_coefficients_tariff_version_id_region_id_key UNIQUE (tariff_version_id, region_id);


--
-- Name: osago_tariff_versions osago_tariff_versions_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_tariff_versions
    ADD CONSTRAINT osago_tariff_versions_pkey PRIMARY KEY (id);


--
-- Name: osago_term_coefficients osago_term_coefficients_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_term_coefficients
    ADD CONSTRAINT osago_term_coefficients_pkey PRIMARY KEY (id);


--
-- Name: osago_term_coefficients osago_term_coefficients_tariff_version_id_months_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_term_coefficients
    ADD CONSTRAINT osago_term_coefficients_tariff_version_id_months_key UNIQUE (tariff_version_id, months);


--
-- Name: osago_unlimited_driver_coefficients osago_unlimited_driver_coefficients_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_unlimited_driver_coefficients
    ADD CONSTRAINT osago_unlimited_driver_coefficients_pkey PRIMARY KEY (tariff_version_id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: policies policies_number_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_number_key UNIQUE (number);


--
-- Name: policies policies_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_pkey PRIMARY KEY (id);


--
-- Name: policy_applications policy_applications_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_pkey PRIMARY KEY (id);


--
-- Name: policy_drivers policy_drivers_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_drivers
    ADD CONSTRAINT policy_drivers_pkey PRIMARY KEY (policy_id, user_id);


--
-- Name: policy_versions policy_versions_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_versions
    ADD CONSTRAINT policy_versions_pkey PRIMARY KEY (id);


--
-- Name: policy_versions policy_versions_policy_id_version_no_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_versions
    ADD CONSTRAINT policy_versions_policy_id_version_no_key UNIQUE (policy_id, version_no);


--
-- Name: ref_policy_terms ref_policy_terms_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_policy_terms
    ADD CONSTRAINT ref_policy_terms_pkey PRIMARY KEY (months);


--
-- Name: ref_regions ref_regions_code_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_regions
    ADD CONSTRAINT ref_regions_code_key UNIQUE (code);


--
-- Name: ref_regions ref_regions_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_regions
    ADD CONSTRAINT ref_regions_pkey PRIMARY KEY (id);


--
-- Name: ref_vehicle_categories ref_vehicle_categories_code_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_vehicle_categories
    ADD CONSTRAINT ref_vehicle_categories_code_key UNIQUE (code);


--
-- Name: ref_vehicle_categories ref_vehicle_categories_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.ref_vehicle_categories
    ADD CONSTRAINT ref_vehicle_categories_pkey PRIMARY KEY (id);


--
-- Name: report_exports report_exports_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_exports
    ADD CONSTRAINT report_exports_pkey PRIMARY KEY (id);


--
-- Name: report_rows report_rows_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_rows
    ADD CONSTRAINT report_rows_pkey PRIMARY KEY (id);


--
-- Name: report_rows report_rows_report_id_metric_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_rows
    ADD CONSTRAINT report_rows_report_id_metric_key UNIQUE (report_id, metric);


--
-- Name: reports reports_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);


--
-- Name: roles roles_code_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.roles
    ADD CONSTRAINT roles_code_key UNIQUE (code);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: chats uq_client_agent; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chats
    ADD CONSTRAINT uq_client_agent UNIQUE (client_id, agent_id);


--
-- Name: vehicles uq_vehicle_reg; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.vehicles
    ADD CONSTRAINT uq_vehicle_reg UNIQUE (reg_number);


--
-- Name: vehicles uq_vehicle_vin; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.vehicles
    ADD CONSTRAINT uq_vehicle_vin UNIQUE (vin);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: vehicles vehicles_pkey; Type: CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.vehicles
    ADD CONSTRAINT vehicles_pkey PRIMARY KEY (id);


--
-- Name: idx_app_attach_app; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_app_attach_app ON insurance.application_attachments USING btree (application_id);


--
-- Name: idx_applications_agent_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_applications_agent_status ON insurance.policy_applications USING btree (assigned_agent_id, status, created_at DESC);


--
-- Name: idx_applications_user; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_applications_user ON insurance.policy_applications USING btree (user_id, created_at DESC);


--
-- Name: idx_chat_messages_chat_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_chat_messages_chat_created ON insurance.chat_messages USING btree (chat_id, created_at);


--
-- Name: idx_chat_messages_sender; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_chat_messages_sender ON insurance.chat_messages USING btree (sender_id);


--
-- Name: idx_chats_agent; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_chats_agent ON insurance.chats USING btree (agent_id);


--
-- Name: idx_chats_client; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_chats_client ON insurance.chats USING btree (client_id);


--
-- Name: idx_claim_attach_claim; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claim_attach_claim ON insurance.claim_attachments USING btree (claim_id);


--
-- Name: idx_claim_attach_claim_type_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claim_attach_claim_type_created ON insurance.claim_attachments USING btree (claim_id, attachment_type, created_at DESC);


--
-- Name: idx_claim_attachments_claim; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claim_attachments_claim ON insurance.claim_attachments USING btree (claim_id, created_at DESC);


--
-- Name: idx_claims_agent_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claims_agent_status ON insurance.claims USING btree (assigned_agent_id, status);


--
-- Name: idx_claims_policy; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claims_policy ON insurance.claims USING btree (policy_id);


--
-- Name: idx_claims_status_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claims_status_created ON insurance.claims USING btree (status, created_at DESC);


--
-- Name: idx_claims_user_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claims_user_created ON insurance.claims USING btree (user_id, created_at DESC);


--
-- Name: idx_claims_user_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_claims_user_status ON insurance.claims USING btree (user_id, status);


--
-- Name: idx_notifications_recipient_unread; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_notifications_recipient_unread ON insurance.notifications USING btree (recipient_id, read_at, created_at DESC);


--
-- Name: idx_notifications_user_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_notifications_user_created ON insurance.notifications USING btree (recipient_id, created_at DESC);


--
-- Name: idx_notifications_user_unread; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_notifications_user_unread ON insurance.notifications USING btree (recipient_id, is_read);


--
-- Name: idx_osago_base_rates_version; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_base_rates_version ON insurance.osago_base_rates USING btree (tariff_version_id);


--
-- Name: idx_osago_calc_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_calc_created ON insurance.osago_calc_requests USING btree (created_at DESC);


--
-- Name: idx_osago_power_coeff_version; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_power_coeff_version ON insurance.osago_power_coefficients USING btree (tariff_version_id, hp_from, hp_to);


--
-- Name: idx_osago_region_coeff_version; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_region_coeff_version ON insurance.osago_region_coefficients USING btree (tariff_version_id);


--
-- Name: idx_osago_tariff_active; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_tariff_active ON insurance.osago_tariff_versions USING btree (is_active);


--
-- Name: idx_osago_term_coeff_version; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_osago_term_coeff_version ON insurance.osago_term_coefficients USING btree (tariff_version_id);


--
-- Name: idx_payments_policy; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_payments_policy ON insurance.payments USING btree (policy_id);


--
-- Name: idx_payments_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_payments_status ON insurance.payments USING btree (status);


--
-- Name: idx_policies_agent; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policies_agent ON insurance.policies USING btree (agent_id);


--
-- Name: idx_policies_user_created; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policies_user_created ON insurance.policies USING btree (user_id, created_at DESC);


--
-- Name: idx_policies_user_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policies_user_status ON insurance.policies USING btree (user_id, status);


--
-- Name: idx_policies_vehicle; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policies_vehicle ON insurance.policies USING btree (vehicle_id);


--
-- Name: idx_policy_drivers_user; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policy_drivers_user ON insurance.policy_drivers USING btree (user_id);


--
-- Name: idx_policy_versions_policy; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_policy_versions_policy ON insurance.policy_versions USING btree (policy_id, created_at DESC);


--
-- Name: idx_report_exports_report; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_report_exports_report ON insurance.report_exports USING btree (report_id, created_at DESC);


--
-- Name: idx_report_rows_report; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_report_rows_report ON insurance.report_rows USING btree (report_id);


--
-- Name: idx_reports_created_by; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_reports_created_by ON insurance.reports USING btree (created_by, created_at DESC);


--
-- Name: idx_reports_period; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_reports_period ON insurance.reports USING btree (period_from, period_to);


--
-- Name: idx_users_assigned_agent; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_users_assigned_agent ON insurance.users USING btree (assigned_agent_id);


--
-- Name: idx_users_status; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_users_status ON insurance.users USING btree (status);


--
-- Name: idx_vehicles_owner; Type: INDEX; Schema: insurance; Owner: -
--

CREATE INDEX idx_vehicles_owner ON insurance.vehicles USING btree (owner_user_id);


--
-- Name: claims trg_claims_set_updated_at; Type: TRIGGER; Schema: insurance; Owner: -
--

CREATE TRIGGER trg_claims_set_updated_at BEFORE UPDATE ON insurance.claims FOR EACH ROW EXECUTE FUNCTION insurance.set_updated_at();


--
-- Name: policies trg_policies_set_number; Type: TRIGGER; Schema: insurance; Owner: -
--

CREATE TRIGGER trg_policies_set_number BEFORE INSERT ON insurance.policies FOR EACH ROW EXECUTE FUNCTION insurance.policies_set_number();


--
-- Name: agent_profiles agent_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.agent_profiles
    ADD CONSTRAINT agent_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: application_attachments application_attachments_application_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.application_attachments
    ADD CONSTRAINT application_attachments_application_id_fkey FOREIGN KEY (application_id) REFERENCES insurance.policy_applications(id) ON DELETE CASCADE;


--
-- Name: chat_messages chat_messages_chat_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chat_messages
    ADD CONSTRAINT chat_messages_chat_id_fkey FOREIGN KEY (chat_id) REFERENCES insurance.chats(id) ON DELETE CASCADE;


--
-- Name: chat_messages chat_messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chat_messages
    ADD CONSTRAINT chat_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: chats chats_agent_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chats
    ADD CONSTRAINT chats_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: chats chats_client_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.chats
    ADD CONSTRAINT chats_client_id_fkey FOREIGN KEY (client_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: claim_attachments claim_attachments_claim_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claim_attachments
    ADD CONSTRAINT claim_attachments_claim_id_fkey FOREIGN KEY (claim_id) REFERENCES insurance.claims(id) ON DELETE CASCADE;


--
-- Name: claims claims_assigned_agent_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims
    ADD CONSTRAINT claims_assigned_agent_id_fkey FOREIGN KEY (assigned_agent_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: claims claims_policy_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims
    ADD CONSTRAINT claims_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES insurance.policies(id) ON DELETE SET NULL;


--
-- Name: claims claims_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.claims
    ADD CONSTRAINT claims_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE RESTRICT;


--
-- Name: client_driver_info client_driver_info_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.client_driver_info
    ADD CONSTRAINT client_driver_info_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: users fk_users_created_by; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by_user_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: insured_person_profiles insured_person_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.insured_person_profiles
    ADD CONSTRAINT insured_person_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (recipient_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: osago_base_rates osago_base_rates_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_base_rates
    ADD CONSTRAINT osago_base_rates_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE CASCADE;


--
-- Name: osago_base_rates osago_base_rates_vehicle_category_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_base_rates
    ADD CONSTRAINT osago_base_rates_vehicle_category_id_fkey FOREIGN KEY (vehicle_category_id) REFERENCES insurance.ref_vehicle_categories(id) ON DELETE RESTRICT;


--
-- Name: osago_calc_requests osago_calc_requests_region_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_region_id_fkey FOREIGN KEY (region_id) REFERENCES insurance.ref_regions(id);


--
-- Name: osago_calc_requests osago_calc_requests_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id);


--
-- Name: osago_calc_requests osago_calc_requests_term_months_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_term_months_fkey FOREIGN KEY (term_months) REFERENCES insurance.ref_policy_terms(months);


--
-- Name: osago_calc_requests osago_calc_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: osago_calc_requests osago_calc_requests_vehicle_category_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_calc_requests
    ADD CONSTRAINT osago_calc_requests_vehicle_category_id_fkey FOREIGN KEY (vehicle_category_id) REFERENCES insurance.ref_vehicle_categories(id);


--
-- Name: osago_power_coefficients osago_power_coefficients_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_power_coefficients
    ADD CONSTRAINT osago_power_coefficients_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE CASCADE;


--
-- Name: osago_region_coefficients osago_region_coefficients_region_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_region_coefficients
    ADD CONSTRAINT osago_region_coefficients_region_id_fkey FOREIGN KEY (region_id) REFERENCES insurance.ref_regions(id) ON DELETE RESTRICT;


--
-- Name: osago_region_coefficients osago_region_coefficients_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_region_coefficients
    ADD CONSTRAINT osago_region_coefficients_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE CASCADE;


--
-- Name: osago_term_coefficients osago_term_coefficients_months_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_term_coefficients
    ADD CONSTRAINT osago_term_coefficients_months_fkey FOREIGN KEY (months) REFERENCES insurance.ref_policy_terms(months) ON DELETE RESTRICT;


--
-- Name: osago_term_coefficients osago_term_coefficients_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_term_coefficients
    ADD CONSTRAINT osago_term_coefficients_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE CASCADE;


--
-- Name: osago_unlimited_driver_coefficients osago_unlimited_driver_coefficients_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.osago_unlimited_driver_coefficients
    ADD CONSTRAINT osago_unlimited_driver_coefficients_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE CASCADE;


--
-- Name: payments payments_policy_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.payments
    ADD CONSTRAINT payments_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES insurance.policies(id) ON DELETE CASCADE;


--
-- Name: policies policies_agent_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_agent_id_fkey FOREIGN KEY (agent_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: policies policies_region_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_region_id_fkey FOREIGN KEY (region_id) REFERENCES insurance.ref_regions(id) ON DELETE SET NULL;


--
-- Name: policies policies_tariff_version_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_tariff_version_id_fkey FOREIGN KEY (tariff_version_id) REFERENCES insurance.osago_tariff_versions(id) ON DELETE SET NULL;


--
-- Name: policies policies_term_months_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_term_months_fkey FOREIGN KEY (term_months) REFERENCES insurance.ref_policy_terms(months) ON DELETE SET NULL;


--
-- Name: policies policies_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE RESTRICT;


--
-- Name: policies policies_vehicle_category_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_vehicle_category_id_fkey FOREIGN KEY (vehicle_category_id) REFERENCES insurance.ref_vehicle_categories(id) ON DELETE SET NULL;


--
-- Name: policies policies_vehicle_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policies
    ADD CONSTRAINT policies_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES insurance.vehicles(id) ON DELETE SET NULL;


--
-- Name: policy_applications policy_applications_assigned_agent_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_assigned_agent_id_fkey FOREIGN KEY (assigned_agent_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: policy_applications policy_applications_calc_request_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_calc_request_id_fkey FOREIGN KEY (calc_request_id) REFERENCES insurance.osago_calc_requests(id) ON DELETE SET NULL;


--
-- Name: policy_applications policy_applications_issued_policy_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_issued_policy_id_fkey FOREIGN KEY (issued_policy_id) REFERENCES insurance.policies(id) ON DELETE SET NULL;


--
-- Name: policy_applications policy_applications_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE RESTRICT;


--
-- Name: policy_applications policy_applications_vehicle_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_applications
    ADD CONSTRAINT policy_applications_vehicle_id_fkey FOREIGN KEY (vehicle_id) REFERENCES insurance.vehicles(id) ON DELETE SET NULL;


--
-- Name: policy_drivers policy_drivers_policy_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_drivers
    ADD CONSTRAINT policy_drivers_policy_fkey FOREIGN KEY (policy_id) REFERENCES insurance.policies(id) ON DELETE CASCADE;


--
-- Name: policy_drivers policy_drivers_user_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_drivers
    ADD CONSTRAINT policy_drivers_user_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE RESTRICT;


--
-- Name: policy_versions policy_versions_created_by_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_versions
    ADD CONSTRAINT policy_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: policy_versions policy_versions_policy_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.policy_versions
    ADD CONSTRAINT policy_versions_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES insurance.policies(id) ON DELETE CASCADE;


--
-- Name: report_exports report_exports_report_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_exports
    ADD CONSTRAINT report_exports_report_id_fkey FOREIGN KEY (report_id) REFERENCES insurance.reports(id) ON DELETE CASCADE;


--
-- Name: report_rows report_rows_report_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.report_rows
    ADD CONSTRAINT report_rows_report_id_fkey FOREIGN KEY (report_id) REFERENCES insurance.reports(id) ON DELETE CASCADE;


--
-- Name: reports reports_created_by_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.reports
    ADD CONSTRAINT reports_created_by_fkey FOREIGN KEY (created_by) REFERENCES insurance.users(id) ON DELETE RESTRICT;


--
-- Name: user_roles user_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES insurance.roles(id) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
-- Name: users users_assigned_agent_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.users
    ADD CONSTRAINT users_assigned_agent_id_fkey FOREIGN KEY (assigned_agent_id) REFERENCES insurance.users(id) ON DELETE SET NULL;


--
-- Name: vehicles vehicles_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: insurance; Owner: -
--

ALTER TABLE ONLY insurance.vehicles
    ADD CONSTRAINT vehicles_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES insurance.users(id) ON DELETE CASCADE;


--
--


