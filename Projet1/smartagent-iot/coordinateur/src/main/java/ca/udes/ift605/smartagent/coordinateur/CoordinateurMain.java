package ca.udes.ift605.smartagent.coordinateur;

import ca.udes.ift605.smartagent.agent.IAgentPiece;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entrée du coordinateur : se connecte au registre RMI (démarré par
 * n'importe lequel des 3 AgentPiece), récupère un stub vers chaque agent,
 * puis exécute un cycle d'équilibrage thermique en boucle.
 *
 * Remarque : le lookup RMI ne récupère JAMAIS l'objet AgentPiece lui-même,
 * seulement un "stub" (un proxy dynamique implémentant IAgentPiece) qui
 * sérialise chaque appel de méthode en message réseau vers la vraie JVM de
 * l'agent. C'est pour ça que le module coordinateur n'a besoin, en dépendance,
 * que de la classe IAgentPiece (l'interface), jamais de AgentPiece.
 */
public class CoordinateurMain {

    private static final int PORT_REGISTRE = 2199;
    private static final long PERIODE_MS = 10_000;
    private static final String[] PIECES = {"salleA", "salleB", "cuisine"};

    public static void main(String[] args) throws Exception {
        Registry registre = LocateRegistry.getRegistry("localhost", PORT_REGISTRE);

        Map<String, IAgentPiece> agents = new HashMap<>();
        for (String piece : PIECES) {
            try {
                IAgentPiece agent = (IAgentPiece) registre.lookup("agent-" + piece);
                agents.put(piece, agent);
                System.out.println("[Coordinateur] connecté à agent-" + piece);
            } catch (RemoteException | NotBoundException e) {
                // On démarre quand même : la Tâche 3.1 exige que le coordinateur
                // continue de fonctionner avec les agents encore disponibles,
                // même si un agent manque déjà au moment du lancement.
                System.err.println("[Coordinateur] agent-" + piece + " indisponible au démarrage: " + e.getMessage());
            }
        }

        // Paires adjacentes imposées par l'énoncé (au minimum).
        List<String[]> pairesAdjacentes = List.of(
                new String[]{"salleA", "salleB"},
                new String[]{"salleB", "cuisine"}
        );

        CoordinateurBatiment coordinateur = new CoordinateurBatiment(agents, pairesAdjacentes);

        System.out.println("Coordinateur démarré, cycle toutes les " + (PERIODE_MS / 1000) + "s. Ctrl+C pour arrêter.");
        while (true) {
            coordinateur.equilibrerTemperatures();
            Thread.sleep(PERIODE_MS);
        }
    }
}
