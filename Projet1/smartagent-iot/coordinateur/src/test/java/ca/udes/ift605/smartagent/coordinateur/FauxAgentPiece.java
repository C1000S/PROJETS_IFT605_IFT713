package ca.udes.ift605.smartagent.coordinateur;

import ca.udes.ift605.smartagent.agent.IAgentPiece;

import java.rmi.RemoteException;

/**
 * Double de test pour IAgentPiece : simule un agent sans RMI ni MQTT.
 * Permet de tester CoordinateurBatiment en isolation complète, y compris le
 * cas de panne (injoignable = true -> lève RemoteException comme le ferait
 * un vrai stub RMI si l'agent distant est mort ou inaccessible).
 */
class FauxAgentPiece implements IAgentPiece {

    private final double temperature;
    private final boolean enTimeout;
    private final boolean injoignable;

    /** Dernière consigne reçue via setConsigneTemperature, ou null si jamais appelée. */
    Double consigneRecue = null;

    FauxAgentPiece(double temperature) {
        this(temperature, false, false);
    }

    FauxAgentPiece(double temperature, boolean enTimeout, boolean injoignable) {
        this.temperature = temperature;
        this.enTimeout = enTimeout;
        this.injoignable = injoignable;
    }

    @Override
    public double getTemperature() throws RemoteException {
        if (injoignable) {
            throw new RemoteException("agent injoignable (simulation de panne pour test)");
        }
        return temperature;
    }

    @Override
    public int getLuminosite() {
        return 0;
    }

    @Override
    public boolean isOccupee() {
        return false;
    }

    @Override
    public void setConsigneTemperature(double t) {
        this.consigneRecue = t;
    }

    @Override
    public String getEtatJson() {
        return "{}";
    }

    @Override
    public boolean estEnTimeout() {
        return enTimeout;
    }
}
