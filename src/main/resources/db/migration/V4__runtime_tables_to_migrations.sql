-- Move runtime-created schema objects into Flyway migrations.

-- Chat message read receipts
create table if not exists insurance.chat_message_reads (
    message_id bigint not null,
    reader_id bigint not null,
    read_at timestamptz not null default now(),
    primary key (message_id, reader_id),
    constraint chat_message_reads_message_fkey
        foreign key (message_id) references insurance.chat_messages(id) on delete cascade,
    constraint chat_message_reads_reader_fkey
        foreign key (reader_id) references insurance.users(id) on delete cascade
);

create index if not exists idx_chat_message_reads_reader
    on insurance.chat_message_reads (reader_id, read_at desc);

-- Chat topic context (selected policy/claim topic)
alter table insurance.chats
    add column if not exists topic_type varchar(24);
alter table insurance.chats
    add column if not exists topic_ref_id bigint;
alter table insurance.chats
    add column if not exists topic_label varchar(255);

-- Claim status history (used by agent/client claim timelines)
create table if not exists insurance.claim_status_history (
    id bigserial primary key,
    claim_id bigint not null,
    old_status insurance.claim_status,
    new_status insurance.claim_status not null,
    comment text,
    created_at timestamptz not null default now(),
    changed_by_user_id bigint,
    constraint claim_status_history_claim_fkey
        foreign key (claim_id) references insurance.claims(id) on delete cascade,
    constraint claim_status_history_changed_by_fkey
        foreign key (changed_by_user_id) references insurance.users(id) on delete set null
);

create index if not exists idx_claim_status_history_claim_created
    on insurance.claim_status_history (claim_id, created_at desc);

-- Claim payout requests (client payout requisites after approval)
create table if not exists insurance.claim_payout_requests (
    id bigserial primary key,
    claim_id bigint not null,
    user_id bigint not null,
    bank_name varchar(200) not null,
    card_masked varchar(32) not null,
    requested_at timestamptz not null default now(),
    status varchar(20) not null default 'REQUESTED',
    constraint claim_payout_requests_claim_fkey
        foreign key (claim_id) references insurance.claims(id) on delete cascade,
    constraint claim_payout_requests_user_fkey
        foreign key (user_id) references insurance.users(id) on delete restrict
);

create index if not exists idx_claim_payout_requests_claim
    on insurance.claim_payout_requests (claim_id, requested_at desc);
