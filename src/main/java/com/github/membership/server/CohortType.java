package com.github.membership.server;

public enum CohortType {
    ONE, TWO, THREE, FOUR;

    public static CohortType fromString(final String cohortTypeString) {
        CohortType foundType = null;
        if (cohortTypeString != null && !cohortTypeString.trim().isEmpty()) {
            for (final CohortType type : CohortType.values()) {
                if (type.name().equalsIgnoreCase(cohortTypeString)) {
                    foundType = type;
                    break;
                }
            }
        }
        return foundType;
    }

}
