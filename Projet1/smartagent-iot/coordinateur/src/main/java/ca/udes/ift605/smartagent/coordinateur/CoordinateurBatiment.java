package ca.udes.ift605.smartagent.coordinateur;

import ca.udes.ift605.smartagent.agent.IAgentPiece;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Applique une politique d'équilibrage thermique entre pièces adjacentes.
 *
 * Politique choisie (choix de conception à justifier dans le rapport) :
 * si l'écart de température entre deux pièces adjacentes dépasse le seuil,
 * on envoie à la pièce la PLUS CHAUDE une nouvelle consigne égale à la
 * MOYENNE des deux températures, plutôt qu'un alignement brutal sur la
 * température de la pièce la plus fraîche. Rationnel : un alignement direct
 * imposerait un choc thermique important à la pièce chaude (ex: 26°C -> 19°C
 * d'un coup) ; converger vers la moyenne rapproche les deux pièces de façon
 * progressive, ce qui est plus réaliste physiquement et laisse le prochain
 * cycle d'équilibrage affiner si l'écart persiste.
 *
 * Robustesse : chaque paire est traitée indépendamment. Si un agent est
 * injoignable (RemoteException), on logue l'erreur et on continue avec les
 * paires suivantes plutôt que d'interrompre tout le cycle — c'est exactement
 * le comportement demandé pour la tolérance aux pannes de la Tâche 3.1,
 * implémenté ici directement plutôt que rajouté après coup.
 */
public class CoordinateurBatiment {

    /** Écart de température (°C) au-delà duquel on déclenche un ajustement. */
    private static final double SEUIL_ECART = 4.0;

    private final Map<String, IAgentPiece> agents;
    private final List<String[]> pairesAdjacentes;

    public CoordinateurBatiment(Map<String, IAgentPiece> agents, List<String[]> pairesAdjacentes) {
        this.agents = agents;
        this.pairesAdjacentes = pairesAdjacentes;
    }

    /** Un cycle complet d'équilibrage sur toutes les paires adjacentes connues. */
    public void equilibrerTemperatures() {
        for (String[] paire : pairesAdjacentes) {
            String nomA = paire[0];
            String nomB = paire[1];
            try {
                traiterPaire(nomA, nomB);
            } catch (RemoteException e) {
                System.err.println("[Coordinateur] Paire " + nomA + "-" + nomB +
                        " ignorée (agent injoignable) : " + e.getMessage());
            }
        }
    }

    private void traiterPaire(String nomA, String nomB) throws RemoteException {
        IAgentPiece agentA = agents.get(nomA);
        IAgentPiece agentB = agents.get(nomB);
        if (agentA == null || agentB == null) {
            System.err.println("[Coordinateur] Agent inconnu dans la paire " + nomA + "-" + nomB);
            return;
        }

        // Tâche 3.1 : on ne bloque jamais sur un capteur en panne. On le
        // signale (pour la traçabilité demandée par l'énoncé) puis on
        // continue avec la dernière valeur connue, exposée par getTemperature()
        // quel que soit son âge — c'est justement le rôle du champ volatile
        // côté AgentPiece de toujours renvoyer quelque chose sans bloquer.
        if (agentA.estEnTimeout()) {
            System.out.println("[Coordinateur] " + nomA + " en timeout (>30s) - utilisation de la dernière valeur connue");
        }
        if (agentB.estEnTimeout()) {
            System.out.println("[Coordinateur] " + nomB + " en timeout (>30s) - utilisation de la dernière valeur connue");
        }

        double tempA = agentA.getTemperature();
        double tempB = agentB.getTemperature();
        double ecart = Math.abs(tempA - tempB);

        if (ecart <= SEUIL_ECART) {
            System.out.printf("[Coordinateur] %s(%.1f) / %s(%.1f) -> écart %.1f (seuil %.1f), rien à faire%n",
                    nomA, tempA, nomB, tempB, ecart, SEUIL_ECART);
            return;
        }

        boolean aEstPlusChaude = tempA > tempB;
        String nomChaud = aEstPlusChaude ? nomA : nomB;
        IAgentPiece agentChaud = aEstPlusChaude ? agentA : agentB;
        double consigneCible = (tempA + tempB) / 2.0;

        agentChaud.setConsigneTemperature(consigneCible);

        System.out.printf(
                "[Coordinateur] DESEQUILIBRE %s(%.1f) / %s(%.1f) -> écart %.1f > seuil %.1f : consigne %.1f envoyée à %s%n",
                nomA, tempA, nomB, tempB, ecart, SEUIL_ECART, consigneCible, nomChaud);
    }
}
