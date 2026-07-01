package com.gerai.projetsservice.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Convertisseur JPA pour sérialiser et désérialiser une {@code List<String>}
 * en une colonne VARCHAR/CLOB Oracle au format CSV.
 * <p>
 * Utilisé sur les champs {@code competences} de {@link Candidate} et {@code options}
 * de {@link ScreeningQuestion}.
 * </p>
 * <p>
 * {@code @Converter} : déclare ce convertisseur auprès du fournisseur JPA (Hibernate).
 * </p>
 *
 * @since 1.0
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    /**
     * Sérialise une liste de chaînes en une chaîne CSV pour stockage Oracle.
     *
     * @param list la liste à sérialiser (peut être nulle ou vide)
     * @return chaîne CSV (ex. {@code "Java,Spring,Angular"}), ou chaîne vide si la liste est vide
     */
    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    /**
     * Désérialise une chaîne CSV Oracle en liste de chaînes.
     *
     * @param data la chaîne CSV stockée en base (peut être nulle ou vide)
     * @return liste de chaînes, vide si la donnée est nulle ou blanche
     */
    @Override
    public List<String> convertToEntityAttribute(String data) {
        if (data == null || data.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }
}
