package com.gerai.demandesservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de calcul des jours ouvrés selon le calendrier tunisien.
 * <p>
 * Le week-end en Tunisie correspond au vendredi et au samedi.
 * La semaine de travail est donc du dimanche au jeudi.
 * Les jours fériés sont chargés depuis la table Oracle {@code GERAI.PUBLIC_HOLIDAYS}
 * et mis en cache par année civile.
 * <p>
 * {@code @Service} : enregistre ce bean dans le contexte Spring.
 * {@code @Slf4j} : active la journalisation via Lombok.
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingDayService {

    /** Source JDBC pour charger les jours fériés depuis {@code GERAI.PUBLIC_HOLIDAYS}. */
    private final JdbcTemplate jdbc;

    /** Cache des jours fériés par année civile (chargé à la première utilisation). */
    private final Map<Integer, Set<LocalDate>> holidayCache = new ConcurrentHashMap<>();

    /**
     * Calcule le nombre de jours ouvrés entre deux dates (incluses).
     * Exclut les vendredis, les samedis et les jours fériés tunisiens.
     *
     * @param startDate date de début (incluse), peut être {@code null}
     * @param endDate   date de fin (incluse), peut être {@code null}
     * @return nombre de jours ouvrés, ou {@code 0} si l'une des dates est nulle ou si {@code endDate} est avant {@code startDate}
     */
    public int countWorkingDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) return 0;
        Set<LocalDate> holidays = getHolidaysForYear(startDate.getYear());
        if (startDate.getYear() != endDate.getYear()) {
            holidays = new HashSet<>(holidays);
            holidays.addAll(getHolidaysForYear(endDate.getYear()));
        }
        int count = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (!isWeekend(current) && !holidays.contains(current)) count++;
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * Retourne l'ensemble des jours fériés tunisiens pour une année donnée,
     * avec mise en cache pour éviter des requêtes Oracle répétées.
     *
     * @param year année civile de référence
     * @return ensemble des dates de jours fériés pour cette année
     */
    public Set<LocalDate> getHolidaysForYear(int year) {
        return holidayCache.computeIfAbsent(year, this::loadHolidays);
    }

    /**
     * Charge les jours fériés depuis la table Oracle {@code GERAI.PUBLIC_HOLIDAYS}.
     * Les jours récurrents (ex. fêtes nationales à date fixe) sont projetés sur l'année donnée.
     * En cas d'erreur Oracle, retourne un ensemble vide et logue un avertissement.
     *
     * @param year année civile pour laquelle charger les jours fériés
     * @return ensemble des dates de jours fériés pour cette année
     */
    private Set<LocalDate> loadHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        try {
            jdbc.queryForList(
                "SELECT HOLIDAY_DATE, RECURRING FROM GERAI.PUBLIC_HOLIDAYS",
                Map.class
            ).forEach(row -> {
                java.sql.Date d = (java.sql.Date) row.get("HOLIDAY_DATE");
                if (d == null) return;
                LocalDate date = d.toLocalDate();
                Number recurring = (Number) row.get("RECURRING");
                if (recurring != null && recurring.intValue() == 1) {
                    // Recurring: same month/day every year
                    holidays.add(LocalDate.of(year, date.getMonthValue(), date.getDayOfMonth()));
                } else if (date.getYear() == year) {
                    holidays.add(date);
                }
            });
        } catch (Exception e) {
            log.warn("[WorkingDayService] Could not load holidays for {}: {}", year, e.getMessage());
        }
        return holidays;
    }

    /**
     * Indique si une date correspond à un jour de week-end tunisien (vendredi ou samedi).
     *
     * @param date date à vérifier
     * @return {@code true} si la date est un vendredi ou un samedi
     */
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY;
    }
}
