package ca.udes.ift605.smartagent.coordinateur;

import ca.udes.ift605.smartagent.agent.IAgentPiece;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires de la POLITIQUE d'équilibrage de CoordinateurBatiment,
 * isolée de RMI et MQTT grâce à FauxAgentPiece.
 *
 * Pourquoi tester ici et pas via RMI/MQTT en direct : un test qui démarre un
 * vrai registre RMI + un vrai broker Mosquitto n'est plus un test UNITAIRE
 * (il devient un test d'intégration, lent et fragile - dépendant de l'ordre
 * de démarrage, de ports libres, etc.). La logique de décision (le calcul du
 * seuil, de la consigne, la gestion des pannes) est elle-même totalement
 * indépendante du transport RMI/MQTT : on peut donc la valider directement
 * contre l'interface IAgentPiece, sans lancer quoi que ce soit.
 * (La validation "en vrai" bout en bout se fait avec AgentPieceTestClient
 * et les scénarios manuels décrits dans le rapport / la démo.)
 */
class CoordinateurBatimentTest {

    @Test
    void aucunAjustementSiEcartSousLeSeuil() {
        FauxAgentPiece salleA = new FauxAgentPiece(20.0);
        FauxAgentPiece salleB = new FauxAgentPiece(22.0); // écart = 2.0 <= 4.0

        creerCoordinateur(salleA, salleB).equilibrerTemperatures();

        assertNull(salleA.consigneRecue, "aucune commande ne doit être envoyée sous le seuil");
        assertNull(salleB.consigneRecue);
    }

    @Test
    void ajusteLaPiecePlusChaudeALaMoyenneSiEcartDepasseLeSeuil() {
        FauxAgentPiece salleA = new FauxAgentPiece(28.0); // plus chaude
        FauxAgentPiece salleB = new FauxAgentPiece(20.0); // écart = 8.0 > 4.0

        creerCoordinateur(salleA, salleB).equilibrerTemperatures();

        assertEquals(24.0, salleA.consigneRecue, 0.01, "consigne = moyenne des deux températures");
        assertNull(salleB.consigneRecue, "la pièce la plus fraîche ne doit jamais recevoir de commande");
    }

    @Test
    void ecartExactementAuSeuilNeDeclenchePasAjustement() {
        FauxAgentPiece salleA = new FauxAgentPiece(24.0);
        FauxAgentPiece salleB = new FauxAgentPiece(20.0); // écart = 4.0 pile (le seuil est ">" strict)

        creerCoordinateur(salleA, salleB).equilibrerTemperatures();

        assertNull(salleA.consigneRecue, "un écart égal au seuil ne doit pas déclencher d'ajustement");
    }

    @Test
    void unAgentInjoignableNInterrompPasLesAutresPaires() {
        FauxAgentPiece salleA = new FauxAgentPiece(0, false, true); // injoignable -> RemoteException
        FauxAgentPiece salleB = new FauxAgentPiece(28.0);
        FauxAgentPiece cuisine = new FauxAgentPiece(20.0); // écart avec salleB = 8.0 > 4.0

        Map<String, IAgentPiece> agents = new HashMap<>();
        agents.put("salleA", salleA);
        agents.put("salleB", salleB);
        agents.put("cuisine", cuisine);

        List<String[]> paires = List.of(
                new String[]{"salleA", "salleB"},
                new String[]{"salleB", "cuisine"}
        );
        CoordinateurBatiment coordinateur = new CoordinateurBatiment(agents, paires);

        assertDoesNotThrow(coordinateur::equilibrerTemperatures,
                "une RemoteException sur une paire ne doit jamais remonter jusqu'à l'appelant (Tâche 3.1)");
        assertEquals(24.0, salleB.consigneRecue, 0.01,
                "la paire salleB-cuisine doit quand même être traitée normalement malgré la panne de salleA");
    }

    private CoordinateurBatiment creerCoordinateur(FauxAgentPiece a, FauxAgentPiece b) {
        Map<String, IAgentPiece> agents = new HashMap<>();
        agents.put("salleA", a);
        agents.put("salleB", b);
        // List.<String[]>of(...) : avec un SEUL tableau en argument, List.of(E...)
        // est ambigu pour le compilateur (élément unique de type String[], ou
        // spreading de ce tableau comme éléments String ?) — le "type witness"
        // explicite lève l'ambiguïté.
        List<String[]> paires = List.<String[]>of(new String[]{"salleA", "salleB"});
        return new CoordinateurBatiment(agents, paires);
    }
}
