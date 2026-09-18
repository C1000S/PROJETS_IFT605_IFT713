package ca.udes.ift605.smartagent.agent;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Client RMI de validation manuelle (PAS un test JUnit automatique : il exige
 * que le registre RMI, les 3 AgentPiece et Mosquitto soient déjà en marche —
 * c'est un scénario d'intégration, pas une unité isolée). Sert à démontrer la
 * validation demandée pour la Tâche 2.2 : lire les 3 états d'une pièce depuis
 * un AUTRE processus, puis modifier sa consigne de thermostat, uniquement via
 * l'interface distante IAgentPiece.
 *
 * Usage : java -cp agent-piece.jar ca.udes.ift605.smartagent.agent.AgentPieceTestClient salleA 21.0
 */
public class AgentPieceTestClient {

    public static void main(String[] args) throws Exception {
        String piece = args.length > 0 ? args[0] : "salleA";
        double nouvelleConsigne = args.length > 1 ? Double.parseDouble(args[1]) : 21.0;

        Registry registre = LocateRegistry.getRegistry("localhost", 2199);
        IAgentPiece agent = (IAgentPiece) registre.lookup("agent-" + piece);

        System.out.println("=== Lecture de l'état de " + piece + " via RMI ===");
        System.out.println("Température : " + agent.getTemperature());
        System.out.println("Luminosité  : " + agent.getLuminosite());
        System.out.println("Occupée     : " + agent.isOccupee());
        System.out.println("getEtatJson : " + agent.getEtatJson());

        System.out.println("=== Envoi d'une nouvelle consigne (" + nouvelleConsigne + ") via RMI ===");
        agent.setConsigneTemperature(nouvelleConsigne);
        System.out.println("Commande envoyée avec succès (vérifiable ensuite sur MQTT/openHAB).");
    }
}
