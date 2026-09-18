package ca.udes.ift605.smartagent.sensor;

/**
 * Point d'entrée : démarre un CapteurSimulateur par pièce demandée (chacun
 * sur son propre thread, puisque CapteurSimulateur.run() boucle indéfiniment
 * et bloquerait les autres pièces s'ils partageaient le même thread).
 *
 * Deux modes d'usage :
 *  - Sans argument : lance les 3 pièces par défaut (salleA, salleB, cuisine)
 *    dans UN SEUL processus — pratique pour une démo rapide "tout en un".
 *  - Avec un argument (ex: "salleA") : lance UNIQUEMENT cette pièce.
 *    Nécessaire pour la Tâche 3.1 (tolérance aux pannes) : pour vraiment
 *    "couper le client MQTT d'une pièce" sans toucher aux deux autres, il
 *    faut que chaque pièce puisse tourner dans un processus indépendant que
 *    l'on peut tuer individuellement (comme les AgentPiece).
 */
public class SensorSimulatorMain {

    private static final String[] PIECES_PAR_DEFAUT = {"salleA", "salleB", "cuisine"};

    public static void main(String[] args) throws Exception {
        String[] pieces = args.length > 0 ? args : PIECES_PAR_DEFAUT;

        for (String piece : pieces) {
            CapteurSimulateur simulateur = new CapteurSimulateur(piece);
            Thread thread = new Thread(simulateur, "sim-" + piece);
            thread.start();
        }
        System.out.println("Simulateur(s) démarré(s) : " + String.join(", ", pieces));
        System.out.println("Ctrl+C pour arrêter.");
    }
}
