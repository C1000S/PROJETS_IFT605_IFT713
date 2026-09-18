package ca.udes.ift605.smartagent.sensor;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Locale;
import java.util.Random;

/**
 * Simule les trois capteurs d'une pièce (température, luminosité, occupation)
 * et publie leurs mesures sur le broker MQTT toutes les 5 secondes.
 *
 * Convention de topics (imposée par l'énoncé) :
 *   batiment/{piece}/capteur/temperature
 *   batiment/{piece}/capteur/luminosite
 *   batiment/{piece}/capteur/occupation
 *
 * Plages de valeurs réalistes documentées (à reprendre dans le rapport, section
 * "choix de conception") :
 *   - température : 17.0 à 26.0 °C, évolution par petits pas (+-0.5°C) pour
 *     rester crédible d'une mesure à l'autre plutôt que de sauter au hasard.
 *   - luminosité   : 0 à 800 lux (pièce sombre -> pièce très éclairée).
 *   - occupation   : booléen tiré avec 40% de chance d'être "occupée".
 */
public class CapteurSimulateur implements Runnable {

    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final long PERIODE_MS = 5000;

    private static final double TEMP_MIN = 17.0;
    private static final double TEMP_MAX = 26.0;
    private static final int LUX_MIN = 0;
    private static final int LUX_MAX = 800;
    private static final double PROBABILITE_OCCUPATION = 0.4;

    private final String piece;
    private final MqttClient client;
    private final Random rnd = new Random();

    // On garde la dernière température publiée pour faire une "marche aléatoire"
    // bornée : un vrai capteur ne saute jamais de 18°C à 25°C en 5 secondes.
    private double derniereTemperature;

    public CapteurSimulateur(String piece) throws MqttException {
        this.piece = piece;

        // clientId doit être unique par connexion MQTT : on suffixe avec le nom
        // de la pièce + un timestamp pour éviter toute collision si on relance
        // rapidement le processus (Mosquitto refuse deux clients avec le même id).
        String clientId = "capteur-" + piece + "-" + System.currentTimeMillis();
        this.client = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());
        this.derniereTemperature = TEMP_MIN + rnd.nextDouble() * (TEMP_MAX - TEMP_MIN);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true); // survit à un redémarrage du broker
        client.connect(options);
        System.out.println("[" + piece + "] connecté au broker " + BROKER_URL);
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Le catch(MqttException) est ICI, À L'INTÉRIEUR de la boucle —
                // pas autour d'elle. Bug réel rencontré en pratique : une simple
                // coupure réseau transitoire (ex. mise en veille de la machine)
                // fait échouer un seul publish() ; si l'exception s'échappe de la
                // boucle, le thread se termine et le "capteur" reste mort pour
                // toujours, même après le rétablissement du réseau. En capturant
                // ici, on perd seulement UN cycle de mesure et on retente au
                // suivant — options.setAutomaticReconnect(true) a le temps de
                // reconnecter le client MQTT en arrière-plan entre-temps.
                try {
                    publishTemp();
                    publishLuminosite();
                    publishOccupation();
                } catch (MqttException e) {
                    System.err.println("[" + piece + "] échec de publication (" + e.getMessage() +
                            "), nouvelle tentative dans " + (PERIODE_MS / 1000) + "s");
                }
                Thread.sleep(PERIODE_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[" + piece + "] simulateur arrêté");
        }
    }

    private void publishTemp() throws MqttException {
        double variation = (rnd.nextDouble() - 0.5); // entre -0.5 et +0.5 °C
        derniereTemperature = clamp(derniereTemperature + variation, TEMP_MIN, TEMP_MAX);
        publier("temperature", String.format(Locale.US, "%.1f", derniereTemperature));
    }

    private void publishLuminosite() throws MqttException {
        int lux = LUX_MIN + rnd.nextInt(LUX_MAX - LUX_MIN + 1);
        publier("luminosite", String.valueOf(lux));
    }

    private void publishOccupation() throws MqttException {
        boolean occupee = rnd.nextDouble() < PROBABILITE_OCCUPATION;
        publier("occupation", String.valueOf(occupee));
    }

    private void publier(String typeCapteur, String valeur) throws MqttException {
        String topic = "batiment/" + piece + "/capteur/" + typeCapteur;
        MqttMessage message = new MqttMessage(valeur.getBytes());
        message.setQos(0);       // "au plus une fois" : suffisant pour des mesures qui se
                                  // répètent toutes les 5s, perdre un message occasionnel n'est pas grave.
        message.setRetained(true); // le broker garde la dernière valeur : un nouvel abonné
                                    // (ex: openHAB qui démarre après le simulateur) reçoit
                                    // immédiatement le dernier état connu au lieu d'attendre 5s.
        client.publish(topic, message);
        System.out.printf("[%s] -> %s = %s%n", piece, topic, valeur);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
