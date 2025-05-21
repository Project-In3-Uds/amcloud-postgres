package cm.amcloud.platform.postgres; // Garde le package existant

import java.io.IOException;
import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class PostgresApplication implements CommandLineRunner { // Implémente CommandLineRunner

    // Constantes pour l'hôte et le port par défaut de PostgreSQL
    private static final String POSTGRES_HOST = "localhost";
    private static final String POSTGRES_PORT = "5432";

    // Charge le fichier .env une seule fois lors de l'initialisation de la classe
    private static final Dotenv dotenv = Dotenv.load();

    public static void main(String[] args) {
        SpringApplication.run(PostgresApplication.class, args);
    }

    /**
     * Cette méthode est exécutée automatiquement par Spring Boot
     * une fois que le contexte de l'application est chargé.
     * @param args Les arguments de la ligne de commande.
     * @throws Exception Si une erreur se produit pendant l'exécution.
     */
    @Override
    public void run(String... args) throws Exception {
        System.out.println("Démarrage de l'installation et de la configuration de PostgreSQL...");
        try {
            // Étape 1: Installer PostgreSQL
            installPostgres();

            // Étape 2: Configurer PostgreSQL en utilisant les informations du .env
            String connectionInfo = configurePostgres();
            System.out.println("\n" + connectionInfo); // Affiche les informations de connexion

        } catch (IOException | InterruptedException e) {
            System.err.println("Une erreur est survenue lors de l'opération PostgreSQL : " + e.getMessage());
            e.printStackTrace();
        } catch (RuntimeException e) {
            System.err.println("Erreur de configuration : " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Fin de l'installation et de la configuration de PostgreSQL.");
    }

    /**
     * Installe PostgreSQL si ce n'est pas déjà fait.
     * @throws IOException Si une erreur d'E/S se produit lors de l'exécution des commandes.
     * @throws InterruptedException Si le processus est interrompu.
     */
    private void installPostgres() throws IOException, InterruptedException {
        // Vérifie si PostgreSQL est déjà installé
        if (isPostgresInstalled()) {
            System.out.println("PostgreSQL est déjà installé. Skip installation.");
            return;
        }

        System.out.println("Mise à jour des paquets...");
        // Exécute la commande de mise à jour des paquets
        runCommand("sudo", "apt", "update");

        System.out.println("Installation de PostgreSQL...");
        // Exécute la commande d'installation de PostgreSQL
        runCommand("sudo", "apt", "install", "-y", "postgresql");

        System.out.println("PostgreSQL installé avec succès.");
    }

    /**
     * Configure PostgreSQL en créant un utilisateur et des bases de données
     * en utilisant les informations du fichier .env.
     *
     * Le fichier .env doit contenir :
     * POSTGRES_USER=votre_utilisateur
     * POSTGRES_PASSWORD=votre_mot_de_passe
     * MAIN_DB_NAME=nom_de_la_premiere_base_de_donnees
     * BILLING_DB_NAME=nom_de_la_seconde_base_de_donnees
     *
     * @return Une chaîne de caractères contenant les informations de connexion (utilisateur, mot de passe, noms des bases de données et leurs URLs).
     * @throws IOException Si une erreur d'E/S se produit lors de l'exécution des commandes.
     * @throws InterruptedException Si le processus est interrompu.
     * @throws RuntimeException Si une variable d'environnement est manquante.
     */
    private String configurePostgres() throws IOException, InterruptedException {
        System.out.println("Chargement des configurations depuis le fichier .env...");

        // Récupère les informations de configuration depuis les variables d'environnement
        String username = dotenv.get("POSTGRES_USER");
        String password = dotenv.get("POSTGRES_PASSWORD");
        String db1 = dotenv.get("MAIN_DB_NAME");
        String db2 = dotenv.get("BILLING_DB_NAME");

        // Vérifie si toutes les variables nécessaires sont présentes
        if (username == null || password == null || db1 == null || db2 == null) {
            throw new RuntimeException("Une ou plusieurs variables d'environnement PostgreSQL sont manquantes dans le fichier .env.");
        }

        System.out.println("Configuration de PostgreSQL avec les informations suivantes :");
        System.out.println("  Utilisateur : " + username);
        System.out.println("  Base de données principale : " + db1);
        System.out.println("  Base de données de facturation : " + db2);

        // Crée l'utilisateur PostgreSQL si il n'existe pas
        if (!userExists(username)) {
            System.out.println("Création de l'utilisateur PostgreSQL '" + username + "'...");
            runCommand("sudo", "-u", "postgres", "psql", "-c", String.format("CREATE USER %s WITH PASSWORD '%s';", username, password));
        } else {
            System.out.println("L'utilisateur PostgreSQL '" + username + "' existe déjà. Skip la création.");
        }

        // Crée la première base de données si elle n'existe pas
        if (!databaseExists(db1)) {
            System.out.println("Création de la base de données '" + db1 + "'...");
            runCommand("sudo", "-u", "postgres", "psql", "-c", String.format("CREATE DATABASE %s OWNER %s;", db1, username));
        } else {
            System.out.println("La base de données '" + db1 + "' existe déjà. Skip la création.");
        }

        // Crée la seconde base de données si elle n'existe pas
        if (!databaseExists(db2)) {
            System.out.println("Création de la base de données '" + db2 + "'...");
            runCommand("sudo", "-u", "postgres", "psql", "-c", String.format("CREATE DATABASE %s OWNER %s;", db2, username));
        } else {
            System.out.println("La base de données '" + db2 + "' existe déjà. Skip la création.");
        }

        System.out.println("Configuration de PostgreSQL terminée avec succès.");

        // Construit les URLs de connexion JDBC
        String mainDbUrl = String.format("jdbc:postgresql://%s:%s/%s", POSTGRES_HOST, POSTGRES_PORT, db1);
        String billingDbUrl = String.format("jdbc:postgresql://%s:%s/%s", POSTGRES_HOST, POSTGRES_PORT, db2);

        // Retourne les informations de connexion, y compris les URLs
        return String.format(
            "Informations de connexion PostgreSQL:\n" +
            "  Utilisateur: %s\n" +
            "  Mot de passe: %s\n" +
            "  Base de données principale: %s\n" +
            "    URL: %s\n" +
            "  Base de données de facturation: %s\n" +
            "    URL: %s",
            username, password, db1, mainDbUrl, db2, billingDbUrl
        );
    }

    /**
     * Exécute une commande système.
     * @param command La commande et ses arguments.
     * @throws IOException Si une erreur d'E/S se produit.
     * @throws InterruptedException Si le processus est interrompu.
     * @throws RuntimeException Si la commande échoue (code de sortie non nul).
     */
    private void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); // Redirige les flux d'entrée/sortie du processus enfant vers le processus parent
        Process process = pb.start();
        int exitCode = process.waitFor(); // Attend la fin du processus

        if (exitCode != 0) {
            // Affiche la commande qui a échoué pour faciliter le débogage
            throw new RuntimeException("La commande a échoué avec le code de sortie " + exitCode + " : " + Arrays.toString(command));
        }
    }

    /**
     * Vérifie si PostgreSQL est installé en cherchant la commande 'psql'.
     * @return true si 'psql' est trouvé, false sinon.
     */
    private boolean isPostgresInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "psql");
            Process process = pb.start();
            // Si 'which psql' retourne 0, cela signifie que psql est dans le PATH
            return process.waitFor() == 0;
        } catch (Exception e) {
            // Si une exception se produit (par exemple, commande non trouvée), PostgreSQL n'est probablement pas installé
            return false;
        }
    }

    /**
     * Vérifie si un utilisateur PostgreSQL existe déjà.
     * @param username Le nom de l'utilisateur à vérifier.
     * @return true si l'utilisateur existe, false sinon.
     */
    private boolean userExists(String username) {
        try {
            // Exécute une requête SQL pour vérifier l'existence de l'utilisateur
            ProcessBuilder pb = new ProcessBuilder("sudo", "-u", "postgres", "psql", "-tAc",
                String.format("SELECT 1 FROM pg_user WHERE usename = '%s';", username));
            Process process = pb.start();
            // Lit la sortie de la commande
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.waitFor() == 0 && output.equals("1");
        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification de l'utilisateur '" + username + "' : " + e.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si une base de données PostgreSQL existe déjà.
     * @param dbName Le nom de la base de données à vérifier.
     * @return true si la base de données existe, false sinon.
     */
    private boolean databaseExists(String dbName) {
        try {
            // Exécute une requête SQL pour vérifier l'existence de la base de données
            ProcessBuilder pb = new ProcessBuilder("sudo", "-u", "postgres", "psql", "-tAc",
                String.format("SELECT 1 FROM pg_database WHERE datname = '%s';", dbName));
            Process process = pb.start();
            // Lit la sortie de la commande
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.waitFor() == 0 && output.equals("1");
        } catch (Exception e) {
            System.err.println("Erreur lors de la vérification de la base de données '" + dbName + "' : " + e.getMessage());
            return false;
        }
    }
}
