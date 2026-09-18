package ca.udes.ift605.smartagent.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.Instant;

/**
 * Un AgentPiece représente UNE SEULE pièce : il s'abonne uniquement aux
 * topics de cette pièce et garde la dernière valeur connue de chaque capteur.
 *
 * Double casquette :
 *  - Côté MQTT : simple abonné (subscriber), reçoit les mesures publiées par
 *    CapteurSimulateur et met à jour son état interne.
 *  - Côté RMI  : serveur distant (UnicastRemoteObject), expose IAgentPiece à
 *    d'autres JVM (le CoordinateurBatiment, ou un client RMI de test).
 *
 * Point de concurrence important : les champs temperature/luminosite/
 * occupation sont ÉCRITS par le thread de callback MQTT (interne à Paho) et
 * LUS par les threads RMI qui exécutent les appels distants (chaque appel
 * RMI entrant tourne sur son propre thread côté serveur). Deux threads
 * différents => risque de "visibilité mémoire" : sans 'volatile', un thread
 * RMI pourrait lire une valeur mise en cache dans son propre cœur de CPU au
 * lieu de la dernière valeur écrite par le thread MQTT. 'volatile' force
 * chaque lecture/écriture à passer par la mémoire principale.
 */
public class AgentPiece extends UnicastRemoteObject implements IAgentPiece {

    private static final String BROKER_URL = "tcp://localhost:1883";

    // Tâche 3.1 : un capteur est considéré en timeout après 30s sans message.
    private static final long TIMEOUT_MS = 30_000;
    // Fréquence de vérification du watchdog : suffisamment fine pour détecter
    // le timeout sans retard perceptible, sans pour autant boucler à vide.
    private static final long PERIODE_SURVEILLANCE_MS = 5_000;

    private final String nom;
    private final MqttClient mqttClient;

    private volatile double temperature = Double.NaN;
    private volatile int luminosite = -1;
    private volatile boolean occupation = false;
    // Horodatage de la dernière mesure reçue, quel que soit le capteur.
    private volatile long dernierMessageMs = 0L;
    // Mémorise si on a DÉJÀ signalé le timeout en cours, pour ne logguer
    // qu'UNE FOIS la transition "panne détectée" et UNE FOIS la "reprise",
    // plutôt que de spammer un message toutes les 5 secondes.
    private volatile boolean timeoutDejaSignale = false;

    public AgentPiece(String nom) throws RemoteException {
        super(); // UnicastRemoteObject : exporte cet objet sur un port RMI (anonyme, différent du port du registre)
        this.nom = nom;
        try {
            String clientId = "agent-" + nom + "-" + System.currentTimeMillis();
            this.mqttClient = new MqttClient(BROKER_URL, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            mqttClient.connect(options);

            // Un seul abonnement avec le wildcard '#' capture les 3 sous-topics
            // (temperature, luminosite, occupation) de CETTE pièce uniquement —
            // l'agent ignore complètement les autres pièces au niveau MQTT.
            String filtre = "batiment/" + nom + "/capteur/#";
            mqttClient.subscribe(filtre, this::onMessageRecu);
            System.out.println("[agent-" + nom + "] abonné à " + filtre);
        } catch (MqttException e) {
            throw new RemoteException("Échec de connexion MQTT pour l'agent " + nom, e);
        }

        // Watchdog : thread daemon dédié qui vérifie périodiquement la
        // fraîcheur des données. Il tourne indépendamment des appels RMI et
        // des callbacks MQTT — la panne d'un capteur n'affecte donc jamais
        // la disponibilité de l'agent lui-même (getTemperature() continue de
        // répondre instantanément avec la dernière valeur connue).
        Thread watchdog = new Thread(this::surveillerTimeout, "watchdog-" + nom);
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void surveillerTimeout() {
        while (true) {
            try {
                Thread.sleep(PERIODE_SURVEILLANCE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            boolean enTimeoutMaintenant = calculerEnTimeout();
            if (enTimeoutMaintenant && !timeoutDejaSignale) {
                timeoutDejaSignale = true;
                System.err.printf("[agent-%s] TIMEOUT détecté : aucun message MQTT depuis plus de %ds. " +
                        "Dernière valeur connue conservée (temperature=%.1f).%n", nom, TIMEOUT_MS / 1000, temperature);
            } else if (!enTimeoutMaintenant && timeoutDejaSignale) {
                timeoutDejaSignale = false;
                System.out.println("[agent-" + nom + "] Reprise : messages MQTT reçus normalement à nouveau.");
            }
        }
    }

    private boolean calculerEnTimeout() {
        return dernierMessageMs != 0 && (System.currentTimeMillis() - dernierMessageMs) > TIMEOUT_MS;
    }

    /** Callback Paho : appelé sur le thread interne du client MQTT à chaque message reçu. */
    private void onMessageRecu(String topic, MqttMessage message) {
        String valeur = new String(message.getPayload());
        String typeCapteur = topic.substring(topic.lastIndexOf('/') + 1);
        try {
            switch (typeCapteur) {
                case "temperature" -> temperature = Double.parseDouble(valeur);
                case "luminosite" -> luminosite = Integer.parseInt(valeur);
                case "occupation" -> occupation = Boolean.parseBoolean(valeur);
                default -> System.err.println("[agent-" + nom + "] topic inattendu: " + topic);
            }
            dernierMessageMs = System.currentTimeMillis();
        } catch (NumberFormatException e) {
            // On log et on ignore plutôt que de laisser l'exception remonter dans le
            // thread de callback MQTT (qui tuerait silencieusement les futurs messages).
            System.err.println("[agent-" + nom + "] valeur invalide reçue sur " + topic + ": " + valeur);
        }
    }

    @Override
    public double getTemperature() throws RemoteException {
        return temperature;
    }

    @Override
    public int getLuminosite() throws RemoteException {
        return luminosite;
    }

    @Override
    public boolean isOccupee() throws RemoteException {
        return occupation;
    }

    @Override
    public void setConsigneTemperature(double t) throws RemoteException {
        String topic = "batiment/" + nom + "/actuateur/thermostat";
        try {
            MqttMessage message = new MqttMessage(String.valueOf(t).getBytes());
            message.setQos(1);        // "au moins une fois" : une commande d'actuateur ne doit pas se perdre silencieusement
            message.setRetained(true);
            mqttClient.publish(topic, message);
            System.out.printf("[agent-%s] nouvelle consigne thermostat = %.1f (topic %s)%n", nom, t, topic);
        } catch (MqttException e) {
            throw new RemoteException("Échec de publication de la consigne pour " + nom, e);
        }
    }

    @Override
    public boolean estEnTimeout() throws RemoteException {
        return calculerEnTimeout();
    }

    @Override
    public String getEtatJson() throws RemoteException {
        JSONObject json = new JSONObject();
        json.put("piece", nom);
        json.put("temperature", temperature);
        json.put("luminosite", luminosite);
        json.put("occupee", occupation);
        json.put("enTimeout", calculerEnTimeout());
        json.put("dernierMessage", dernierMessageMs == 0
                ? "aucun"
                : Instant.ofEpochMilli(dernierMessageMs).toString());
        return json.toString();
    }
}
