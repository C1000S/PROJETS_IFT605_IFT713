package ca.udes.ift605.smartagent.agent;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Contrat RMI d'un agent de pièce.
 *
 * Concept clé : "extends Remote" est un marqueur — il ne définit aucune
 * méthode lui-même, mais il dit à RMI "cette interface peut être appelée
 * à distance". Toute méthode déclarée ici DOIT lister RemoteException,
 * même si l'implémentation ne l'utilise pas explicitement : un appel RMI
 * traverse le réseau et peut échouer à tout moment (broker down, agent
 * planté, coupure réseau) — contrairement à un appel de méthode local qui
 * ne peut échouer que par un bug dans le code appelé.
 *
 * Remarque sur la méthode de résumé d'état : l'énoncé la nomme getEtatJson()
 * dans la section "Contrat RMI à respecter", alors que le squelette de code
 * fourni plus bas utilise getEtat(). On suit ici la description textuelle
 * (getEtatJson) car c'est elle qui fixe le "contrat à respecter" ; à noter
 * dans le rapport comme incohérence relevée dans l'énoncé.
 */
public interface IAgentPiece extends Remote {

    double getTemperature() throws RemoteException;

    int getLuminosite() throws RemoteException;

    boolean isOccupee() throws RemoteException;

    void setConsigneTemperature(double t) throws RemoteException;

    /** Résumé JSON de l'état courant de la pièce (température, luminosité, occupation...). */
    String getEtatJson() throws RemoteException;

    /**
     * Tâche 3.1 — vrai si aucun message MQTT n'a été reçu depuis plus de 30s.
     * Ajoutée après coup au contrat initial de la Tâche 2.1 : ce n'est pas
     * une violation du contrat RMI (les 5 méthodes d'origine gardent leur
     * signature exacte), juste une extension pour le besoin de la Semaine 3.
     */
    boolean estEnTimeout() throws RemoteException;
}
