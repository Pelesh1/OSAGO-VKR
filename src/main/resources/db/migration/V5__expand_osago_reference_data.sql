-- Expand OSAGO references: more regions and vehicle categories used in calculator.

insert into insurance.ref_vehicle_categories (code, name, is_active)
values
    ('A', 'Мотоциклы и мотороллеры', true),
    ('B', 'Легковой автомобиль', true),
    ('BE', 'Легковой с прицепом', true),
    ('C', 'Грузовой автомобиль', true),
    ('CE', 'Грузовой с прицепом', true),
    ('D', 'Автобус', true),
    ('DE', 'Автобус с прицепом', true),
    ('TB', 'Троллейбус', true),
    ('TM', 'Трамвай', true)
on conflict (code) do update
set name = excluded.name,
    is_active = true;

insert into insurance.ref_regions (code, name, is_active)
values
    ('MOW', 'Москва', true),
    ('SPE', 'Санкт-Петербург', true),
    ('MOS', 'Московская область', true),
    ('LEN', 'Ленинградская область', true),
    ('SVE', 'Свердловская область', true),
    ('TAT', 'Республика Татарстан', true),
    ('KDA', 'Краснодарский край', true),
    ('NVS', 'Новосибирская область', true),
    ('SAM', 'Самарская область', true),
    ('PER', 'Пермский край', true),
    ('CHE', 'Челябинская область', true),
    ('BA', 'Республика Башкортостан', true),
    ('RO', 'Ростовская область', true),
    ('NN', 'Нижегородская область', true),
    ('KEM', 'Кемеровская область', true),
    ('KHA', 'Хабаровский край', true),
    ('PRI', 'Приморский край', true),
    ('IRK', 'Иркутская область', true),
    ('VOR', 'Воронежская область', true),
    ('KRA', 'Красноярский край', true)
on conflict (code) do update
set name = excluded.name,
    is_active = true;

do $$
declare
    tv_id bigint;
begin
    for tv_id in
        select id
        from insurance.osago_tariff_versions
        where is_active = true
          and valid_from <= current_date
          and (valid_to is null or valid_to >= current_date)
    loop
        insert into insurance.osago_base_rates (tariff_version_id, vehicle_category_id, base_rate)
        select
            tv_id,
            c.id,
            case c.code
                when 'A' then 1900.00
                when 'B' then 5000.00
                when 'BE' then 5300.00
                when 'C' then 7800.00
                when 'CE' then 8200.00
                when 'D' then 8200.00
                when 'DE' then 8600.00
                when 'TB' then 4200.00
                when 'TM' then 4100.00
                else 5000.00
            end
        from insurance.ref_vehicle_categories c
        where c.is_active = true
          and not exists (
              select 1
              from insurance.osago_base_rates br
              where br.tariff_version_id = tv_id
                and br.vehicle_category_id = c.id
          );

        insert into insurance.osago_region_coefficients (tariff_version_id, region_id, coefficient)
        select
            tv_id,
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
                when 'CHE' then 1.50
                when 'BA' then 1.45
                when 'RO' then 1.42
                when 'NN' then 1.45
                when 'KEM' then 1.38
                when 'KHA' then 1.36
                when 'PRI' then 1.34
                when 'IRK' then 1.39
                when 'VOR' then 1.41
                when 'KRA' then 1.43
                else 1.00
            end
        from insurance.ref_regions r
        where r.is_active = true
          and not exists (
              select 1
              from insurance.osago_region_coefficients rc
              where rc.tariff_version_id = tv_id
                and rc.region_id = r.id
          );
    end loop;
end
$$;

-- Hide legacy duplicate codes from UI selections (keep historical rows for old records).
update insurance.ref_vehicle_categories
set is_active = false
where code in ('PASSENGER');

update insurance.ref_regions
set is_active = false
where code in ('RU-MOW', 'RU-SVE');
