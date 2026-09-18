package ca.udes.ift605.smartagent.agent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;

/**
 * Lance UN agent pour UNE pièce, dans son propre processus JVM — c'est
 * exactement ce que demande l'énoncé : "Java RMI (appels de méthodes
 * distants entre JVM)". Pour la démo, on lance donc 3 fois ce programme
 * (un par pièce), chacun dans un terminal séparé.
 *
 * Détail RMI important : le "registre" (port 2199) est un service d'annuaire
 * UNIQUE, partagé par les 3 agents — mais un seul processus peut le créer.
 * Comme les 3 agents démarrent indépendamment, on ne sait pas lequel sera
 * lancé en premier : on essaie donc de le CRÉER, et si le port est déjà pris
 * (ExportException) c'est qu'un autre agent l'a déjà créé, donc on s'y
 * CONNECTE simplement à la place.
 *
 * Usage : java -jar agent-piece.jar salleA
 */
public class AgentPieceMain {

    private static final int PORT_REGISTRE = 2199;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: AgentPieceMain <salleA|salleB|cuisine>");
            System.exit(1);
        }
        String piece = args[0];

        Registry registre = obtenirOuCreerRegistre();

        AgentPiece agent = new AgentPiece(piece);
        String nomEnregistrement = "agent-" + piece;
        registre.rebind(nomEnregistrement, agent);

        System.out.println("AgentPiece[" + piece + "] enregistré sous '" + nomEnregistrement +
                "' dans le registre RMI (localhost:" + PORT_REGISTRE + ")");
        System.out.println("En attente d'appels RMI et de messages MQTT... (Ctrl+C pour arrêter)");
    }

    private static Registry obtenirOuCreerRegistre() throws Exception {
        try {
            Registry registre = LocateRegistry.createRegistry(PORT_REGISTRE);
            System.out.println("Registre RMI créé sur le port " + PORT_REGISTRE);
            return registre;
        } catch (ExportException dejaActif) {
            System.out.println("Registre RMI déjà actif sur le port " + PORT_REGISTRE + ", connexion...");
            return LocateRegistry.getRegistry(PORT_REGISTRE);
        }
    }
}
