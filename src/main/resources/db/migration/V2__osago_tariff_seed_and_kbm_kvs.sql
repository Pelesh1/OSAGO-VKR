create table if not exists insurance.osago_kvs_coefficients (
    id bigserial primary key,
    tariff_version_id bigint not null,
    age_from integer not null,
    age_to integer,
    exp_from integer not null,
    exp_to integer,
    coefficient numeric(8,4) not null,
    constraint chk_osago_kvs_age check (age_to is null or age_to >= age_from),
    constraint chk_osago_kvs_exp check (exp_to is null or exp_to >= exp_from),
    constraint chk_osago_kvs_non_negative check (age_from >= 0 and exp_from >= 0)
);

create index if not exists idx_osago_kvs_coeff_version
    on insurance.osago_kvs_coefficients (tariff_version_id, age_from, age_to, exp_from, exp_to);

create table if not exists insurance.osago_kbm_coefficients (
    tariff_version_id bigint not null,
    class_code varchar(4) not null,
    coefficient numeric(8,4) not null,
    constraint osago_kbm_coefficients_pkey primary key (tariff_version_id, class_code)
);

alter table insurance.osago_kvs_coefficients
    drop constraint if exists osago_kvs_coefficients_tariff_version_id_fkey;
alter table insurance.osago_kvs_coefficients
    add constraint osago_kvs_coefficients_tariff_version_id_fkey
    foreign key (tariff_version_id) references insurance.osago_tariff_versions(id) on delete cascade;

alter table insurance.osago_kbm_coefficients
    drop constraint if exists osago_kbm_coefficients_tariff_version_id_fkey;
alter table insurance.osago_kbm_coefficients
    add constraint osago_kbm_coefficients_tariff_version_id_fkey
    foreign key (tariff_version_id) references insurance.osago_tariff_versions(id) on delete cascade;

alter table insurance.osago_calc_requests add column if not exists driver_birth_date date;
alter table insurance.osago_calc_requests add column if not exists license_issued_date date;
alter table insurance.osago_calc_requests add column if not exists kbm_class_code varchar(4);
alter table insurance.osago_calc_requests add column if not exists coeff_kvs numeric(8,4);
alter table insurance.osago_calc_requests add column if not exists coeff_kbm numeric(8,4);

insert into insurance.ref_vehicle_categories (code, name, is_active)
values
    ('B', 'Легковой автомобиль', true),
    ('C', 'Грузовой автомобиль', true),
    ('D', 'Автобус', true),
    ('MOTO', 'Мотоцикл', true)
on conflict (code) do update
set name = excluded.name,
    is_active = true;

insert into insurance.ref_policy_terms (months, name, is_active)
values
    (3, '3 месяца', true),
    (4, '4 месяца', true),
    (5, '5 месяцев', true),
    (6, '6 месяцев', true),
    (7, '7 месяцев', true),
    (8, '8 месяцев', true),
    (9, '9 месяцев', true),
    (10, '10 месяцев', true),
    (11, '11 месяцев', true),
    (12, '12 месяцев', true)
on conflict (months) do update
set name = excluded.name,
    is_active = true;

insert into insurance.ref_regions (code, name, is_active)
values
    ('MOW', 'Москва', true),
    ('SPE', 'Санкт-Петербург', true),
    ('LEN', 'Ленинградская область', true),
    ('MOS', 'Московская область', true),
    ('SVE', 'Свердловская область', true),
    ('TAT', 'Республика Татарстан', true),
    ('KDA', 'Краснодарский край', true),
    ('NVS', 'Новосибирская область', true),
    ('SAM', 'Самарская область', true),
    ('PER', 'Пермский край', true)
on conflict (code) do update
set name = excluded.name,
    is_active = true;

do $$
declare
    tv_id bigint;
begin
    select id
    into tv_id
    from insurance.osago_tariff_versions
    where is_active = true
      and valid_from <= current_date
      and (valid_to is null or valid_to >= current_date)
    order by valid_from desc, id desc
    limit 1;

    if tv_id is null then
        insert into insurance.osago_tariff_versions (name, valid_from, valid_to, is_active, created_at)
        values ('Базовый тариф ОСАГО', date '2026-01-01', null, true, now())
        returning id into tv_id;
    end if;

    if not exists (
        select 1
        from insurance.osago_base_rates br
        join insurance.ref_vehicle_categories c on c.id = br.vehicle_category_id
        where br.tariff_version_id = tv_id and c.code = 'B'
    ) then
        insert into insurance.osago_base_rates (tariff_version_id, vehicle_category_id, base_rate)
        select tv_id,
               c.id,
               case c.code
                   when 'B' then 5000.00
                   when 'C' then 7800.00
                   when 'D' then 8200.00
                   when 'MOTO' then 1800.00
                   else 5000.00
               end
        from insurance.ref_vehicle_categories c
        where c.is_active = true;
    end if;

    insert into insurance.osago_region_coefficients (tariff_version_id, region_id, coefficient)
    select tv_id,
           r.id,
           case r.code
               when 'MOW' then 1.80
               when 'SPE' then 1.64
               when 'MOS' then 1.56
               when 'LEN' then 1.46
               when 'SVE' then 1.64
               when 'TAT' then 1.64
               when 'KDA' then 1.56
               when 'NVS' then 1.56
               when 'SAM' then 1.48
               when 'PER' then 1.40
               else 1.00
           end
    from insurance.ref_regions r
    where r.is_active = true
      and not exists (
          select 1
          from insurance.osago_region_coefficients x
          where x.tariff_version_id = tv_id
            and x.region_id = r.id
      );

    if not exists (
        select 1 from insurance.osago_power_coefficients where tariff_version_id = tv_id
    ) then
        insert into insurance.osago_power_coefficients (tariff_version_id, hp_from, hp_to, coefficient)
        values
            (tv_id, 1, 50, 0.60),
            (tv_id, 51, 70, 1.00),
            (tv_id, 71, 100, 1.10),
            (tv_id, 101, 120, 1.20),
            (tv_id, 121, 150, 1.40),
            (tv_id, 151, null, 1.60);
    end if;

    insert into insurance.osago_term_coefficients (tariff_version_id, months, coefficient)
    select tv_id,
           t.months,
           case t.months
               when 3 then 0.50
               when 4 then 0.60
               when 5 then 0.65
               when 6 then 0.70
               when 7 then 0.80
               when 8 then 0.90
               when 9 then 0.95
               else 1.00
           end
    from insurance.ref_policy_terms t
    where t.is_active = true
      and not exists (
          select 1
          from insurance.osago_term_coefficients x
          where x.tariff_version_id = tv_id
            and x.months = t.months
      );

    insert into insurance.osago_unlimited_driver_coefficients (tariff_version_id, coeff_limited, coeff_unlimited)
    values (tv_id, 1.0000, 2.3200)
    on conflict (tariff_version_id) do update
    set coeff_limited = excluded.coeff_limited,
        coeff_unlimited = excluded.coeff_unlimited;

    if not exists (
        select 1 from insurance.osago_kvs_coefficients where tariff_version_id = tv_id
    ) then
        insert into insurance.osago_kvs_coefficients (tariff_version_id, age_from, age_to, exp_from, exp_to, coefficient)
        values
            (tv_id, 0, 21, 0, 1, 2.27),
            (tv_id, 0, 21, 2, null, 1.92),
            (tv_id, 22, 29, 0, 1, 1.88),
            (tv_id, 22, 29, 2, null, 1.72),
            (tv_id, 30, null, 0, 1, 1.66),
            (tv_id, 30, null, 2, null, 1.00);
    end if;

    insert into insurance.osago_kbm_coefficients (tariff_version_id, class_code, coefficient)
    values
        (tv_id, 'M', 3.92),
        (tv_id, '0', 2.94),
        (tv_id, '1', 2.25),
        (tv_id, '2', 1.76),
        (tv_id, '3', 1.17),
        (tv_id, '4', 1.00),
        (tv_id, '5', 0.91),
        (tv_id, '6', 0.83),
        (tv_id, '7', 0.78),
        (tv_id, '8', 0.74),
        (tv_id, '9', 0.68),
        (tv_id, '10', 0.63),
        (tv_id, '11', 0.57),
        (tv_id, '12', 0.52),
        (tv_id, '13', 0.46)
    on conflict (tariff_version_id, class_code) do update
    set coefficient = excluded.coefficient;
end
$$;
