package com.gerai.demandesservice.exception;

/**
 * Exception levée lorsqu'un employé dépasse son quota de congés autorisé
 * pour un type de congé donné sur une année civile.
 * <p>
 * Traitée par {@link com.gerai.demandesservice.controller.GlobalExceptionHandler}
 * qui retourne un code HTTP 422 Unprocessable Entity avec le détail du quota restant.
 *
 * @since 1.0
 */
public class QuotaExceededException extends RuntimeException {

    /** Code textuel du type de congé concerné (ex : {@code ANNUEL}, {@code HAJJ}). */
    private final String leaveType;

    /** Nombre de jours restants dans le quota annuel pour ce type de congé. */
    private final int    remainingDays;

    /**
     * Construit une exception de dépassement de quota.
     *
     * @param message      message décrivant le dépassement
     * @param leaveType    code textuel du type de congé concerné
     * @param remainingDays nombre de jours restants dans le quota (0 si épuisé)
     */
    public QuotaExceededException(String message, String leaveType, int remainingDays) {
        super(message);
        this.leaveType     = leaveType;
        this.remainingDays = remainingDays;
    }

    /**
     * Retourne le code textuel du type de congé qui a provoqué le dépassement.
     *
     * @return code du type de congé (ex : {@code ANNUEL}, {@code HAJJ})
     */
    public String getLeaveType()    { return leaveType;     }

    /**
     * Retourne le nombre de jours restants dans le quota annuel.
     *
     * @return jours restants (0 si le quota est totalement épuisé)
     */
    public int    getRemainingDays(){ return remainingDays; }
}
