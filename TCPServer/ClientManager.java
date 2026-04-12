import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


//Un client manager créé par client, ils fonctionnent en parralèle sur des threads différents
// gerent les interactions avec les clients puisque le serveur doit juste coordonner 

public class ClientManager implements Runnable {
    private Socket socket;
    private BufferedReader in;  // utilisé pour recevoir les messages
    private PrintWriter out;  // utilisé pour envoyer les messages
    private String playerName;
    private boolean isGaming = false;
    private PlayerVSComputer currentGame;
    private String ipAddress;
    private int p2pPort;

    public ClientManager(Socket socket) {
        this.socket = socket;
        this.ipAddress = socket.getInetAddress().getHostAddress();
    }


    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                processMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected unexpectedly."); // detecter une deconnexion
        } finally {}
    }

    private void processMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2 || !parts[0].equals("GG")) return; //si le message est pas du bon format on ignore

        String command = parts[1]; // comme c'est la deuxieme partie la plus importante on regarde que celle la pour le moment

        // envoyer des messages d'erreur si la requete n'est pas possible
        switch (command) {
            case "CONNECT":
                if (parts.length < 4) {
                    sendMessage("GG|ERROR|Format: GG|CONNECT|Name|P2PPort");
                    return;
                }
                this.playerName = parts[2];
                this.p2pPort = Integer.parseInt(parts[3]);
                TCPServer.activeClients.put(this.playerName, this);

                System.out.println("[CONNEXION] Joueur '" + this.playerName + "' connecté avec le port P2P: " + this.p2pPort);
                sendMessage("GG|CONNECTED|" + this.playerName);
                break;

            case "CREATE_ROOM":
                if (parts.length < 5) {
                    sendMessage("GG|ERROR|Format: GG|CREATE_ROOM|Name|MaxPlayers|MaxAttempts");
                    return;
                }
                String roomName = parts[2];
                int maxPlayers = Integer.parseInt(parts[3]);
                int maxAttempts = Integer.parseInt(parts[4]);

                TCPServer.activeRooms.put(roomName, new GameRoom(roomName, this.playerName, maxPlayers, maxAttempts));

                System.out.println("[SALLE] '" + this.playerName + "' a créé la salle '" + roomName + "' (Max: " + maxPlayers + " joueurs, " + maxAttempts + " essais)");
                sendMessage("GG|ROOM_CREATED|" + roomName);
                break;

            case "LIST_ROOMS":
                System.out.println("[INFO] '" + this.playerName + "' demande la liste des salles.");
                String roomList = String.join(",", TCPServer.activeRooms.keySet());
                sendMessage("GG|ROOM_LIST|" + roomList);
                break;

            case "JOIN_ROOM":
                String targetRoomName = parts[2];
                GameRoom roomToJoin = TCPServer.activeRooms.get(targetRoomName);

                // Vérifier si la salle existe
                if (roomToJoin == null) {
                    System.out.println("[ERREUR] '" + this.playerName + "' tente de rejoindre une salle inexistante : " + targetRoomName);
                    sendMessage("GG|ERROR|La salle '" + targetRoomName + "' n'existe pas.");
                }
                // Vérifier si le joueur est déjà présent dans la liste des joueurs de cette salle
                else if (roomToJoin.players.contains(this.playerName)) {
                    System.out.println("[ERREUR] '" + this.playerName + "' tente de rejoindre '" + targetRoomName + "' mais y est déjà.");
                    sendMessage("GG|ERROR|Vous êtes déjà dans cette salle !");
                }
                // Tenter d'ajouter le joueur
                else if (roomToJoin.addPlayer(this.playerName)) {
                    System.out.println("[REJOINDRE] '" + this.playerName + "' a rejoint la salle '" + targetRoomName + "'");
                    sendMessage("GG|JOINED_ROOM|" + roomToJoin.roomName + "|" + roomToJoin.getPlayersListStr());
                }
                // Si addPlayer retourne false, c'est que la salle est pleine
                else {
                    System.out.println("[ERREUR] '" + this.playerName + "' n'a pas pu rejoindre '" + targetRoomName + "' (Salle pleine)");
                    sendMessage("GG|ERROR|La salle '" + targetRoomName + "' est complète (" + roomToJoin.maxPlayers + " max).");
                }
                break;
            case "LEAVE_ROOM":
                GameRoom roomToLeave = TCPServer.activeRooms.get(parts[2]);
                if (roomToLeave != null) {
                    synchronized (roomToLeave) { 
                        roomToLeave.removePlayer(this.playerName);
                        System.out.println("[DÉPART] '" + this.playerName + "' a quitté '" + parts[2] + "'");

                        //Si la salle est vide, on la supprime
                        if (roomToLeave.players.isEmpty()) {
                            TCPServer.activeRooms.remove(parts[2]);
                            System.out.println("[INFO] Salle '" + parts[2] + "' supprimée (vide).");
                        }
                        else {
                            // Si celui qui part est l'admin, on donne les droits au premier joueur restant
                            if (this.playerName.equals(roomToLeave.adminName)) {
                                String newAdmin = roomToLeave.players.get(0);
                                roomToLeave.adminName = newAdmin;

                                System.out.println("[INFO] Nouvel admin pour '" + parts[2] + "' : " + newAdmin);
                                for (String pName : roomToLeave.players) {
                                    ClientManager pClient = TCPServer.activeClients.get(pName);

                                    if (pClient != null) {
                                        if (pName.equals(roomToLeave.adminName)) {

                                            pClient.sendMessage("GG|NEW_ADMIN|" + roomToLeave.roomName + "|Vous êtes maintenant l'administrateur de la salle.");
                                        } else {

                                            pClient.sendMessage("GG|ADMIN_CHANGED|" + roomToLeave.adminName + "|est le nouvel administrateur de la salle.");
                                        }
                                    }
                                }
                            }
                        }
                    }
                    sendMessage("GG|LEAVE_ROOM|" + parts[2] + " :A BIEN ÉTÉ QUITTÉ");
                }
                break;

            case "PLAY_SERVER":
                try {
                    int nbTentatives = Integer.parseInt(parts[2]);
                    this.currentGame = new PlayerVSComputer(nbTentatives);
                    this.isGaming = true;
                    System.out.println("[SOLO] '" + this.playerName + "' lance une partie contre l'ordinateur (" + nbTentatives + " essais)");
                    sendMessage("GG|GAME_STARTED|Bonne chance " + this.playerName);
                } catch (Exception e) {
                    sendMessage("GG|ERROR|Usage: GG|PLAY_SERVER|nbTentatives");
                }
                break;

            case "GUESS":
                if (this.currentGame != null && parts.length >= 6) {
                    System.out.println("[JEU] Essai reçu de '" + this.playerName + "': " + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5]);
                    String[] playerGuess = {parts[2], parts[3], parts[4], parts[5]};
                    String response = this.currentGame.handleGuess(playerGuess);
                    sendMessage(response);

                    if (this.currentGame.isGameOver()) {
                        System.out.println("[JEU] Partie terminée pour '" + this.playerName + "'. Résultat envoyé.");
                        this.currentGame = null;
                        this.isGaming = false;
                    }
                } else {
                    System.out.println("[ERREUR] '" + this.playerName + "' tente de deviner sans partie active ou format invalide.");
                    sendMessage("GG|ERROR|Aucune partie active ou format incorrect.");
                }
                break;

            case "KICK_PLAYER":
                if (parts.length < 4) {
                    sendMessage("GG|ERROR|Format: GG|KICK_PLAYER|RoomName|PlayerName");
                    break;
                }

                String roomname = parts[2];
                String playerToKick = parts[3];
                GameRoom roomToKickFrom = TCPServer.activeRooms.get(roomname);

                //Vérifier si la salle existe
                if (roomToKickFrom == null) {
                    sendMessage("GG|ERROR|La salle '" + roomname + "' n'existe pas.");
                    break;
                }

                //Vérifier si l'envoyeur est bien l'admin
                if (!roomToKickFrom.adminName.equals(this.playerName)) {
                    System.out.println("[ALERTE] Tentative de kick non-autorisée par '" + this.playerName + "'");
                    sendMessage("GG|ERROR|Action refusée : Vous n'êtes pas l'administrateur de cette salle.");
                    break;
                }

                //Procéde à l'expulsion
                if (roomToKickFrom.players.contains(playerToKick)) {
                    roomToKickFrom.removePlayer(playerToKick);


                    ClientManager kickedClient = TCPServer.activeClients.get(playerToKick);
                    if (kickedClient != null) {
                        kickedClient.sendMessage("GG|PLAYER_KICKED|" + playerToKick);
                    }

                    //Confirmer à l'admin
                    System.out.println("[MODÉRATION] Admin '" + this.playerName + "' a expulsé '" + playerToKick + "'");
                    sendMessage("GG|KICK_SUCCESS|" + playerToKick + "|A été retiré de la salle.");
                } else {
                    sendMessage("GG|ERROR|Le joueur '" + playerToKick + "' n'est pas dans cette salle.");
                }
                break;

            case "START_GAME":
                if (parts.length < 3) return;
                GameRoom roomToStart = TCPServer.activeRooms.get(parts[2]);

                if (roomToStart != null && roomToStart.adminName.equals(this.playerName)) {
                    System.out.println("[DÉMARRAGE] La partie commence dans la salle '" + roomToStart.roomName + "' !");
                    String playerList = roomToStart.getPlayersListStr();


                    for (String pNameA : roomToStart.players) {
                        ClientManager clientA = TCPServer.activeClients.get(pNameA);
                        if (clientA == null) continue;

                        // Envoyer le signal de départ au joueur
                        clientA.sendMessage("GG|GAME_STARTED|" + roomToStart.roomName + "|" + playerList + "|" + roomToStart.maxAttempts);

                        // Pour chaque jouer on lui envoie les infos de connexion de tous les joueurs
                        for (String pNameB : roomToStart.players) {
                            if (!pNameA.equals(pNameB)) {
                                ClientManager clientB = TCPServer.activeClients.get(pNameB);
                                if (clientB != null) {
                                    System.out.println("[P2P] Info envoyée à " + pNameA + " pour se connecter à " + pNameB);
                                    clientA.sendMessage("GG|CONNECT_PEER|" + clientB.playerName + "|" + clientB.ipAddress + "|" + clientB.p2pPort);
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("[REFUS] '" + this.playerName + "' a tenté de lancer une salle dont il n'est pas admin.");
                    sendMessage("GG|ERROR|Action refusée : Seul l'administrateur (" + roomToStart.adminName + ") peut lancer la partie.");
                }
                break;

            default:
                System.out.println("[ALERTE] Commande inconnue de '" + (this.playerName != null ? this.playerName : "Inconnu") + "': " + command);
        }
    }

    private void sendMessage(String msg) {
        out.println(msg);
    }
}
