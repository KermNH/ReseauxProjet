import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


//Un client manager créé par client, ils fonctionnent en parralèle sur des threads différents
// gerent les interactions avec les clients puisque le serveur doit juste coordonner 




//interface Runnable permet l'utilisation d'instances de cette classe dans des threads
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

    // run vient avec l'interface Runnable, est executé quand le thread démare
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
            System.out.println("Client disconnected unexpectedly."); // pour detecter une deconnexion
        } finally {}
    }

    private void processMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2 || !parts[0].equals("GG")) return; //si le message est pas du bon format on ignore

        String command = parts[1]; // comme c'est la deuxieme partie la plus importante on regarde que celle la pour le moment

        //gestion des commandes client, pourrait etre pertinent d'ajouter des vérifications sur les arguments 
        // genre pas de noms vides, des int là ou il faut etc
        // et envoyer des messages d'erreur si requete pas possible (genre room pleine)
        switch (command) {
            case "CONNECT":
                if (parts.length < 4) {
                    sendMessage("GG|ERROR|Format: GG|CONNECT|Name|P2PPort");
                    return;
                }
                this.playerName = parts[2];
                this.p2pPort = Integer.parseInt(parts[3]); // Only if you added P2P logic
                TCPServer.activeClients.put(this.playerName, this);
                sendMessage("GG|CONNECTED|" + this.playerName);
                break;
            case "CREATE_ROOM":
                // Guard 3: Ensure we have RoomName, MaxPlayers, and MaxAttempts (5 parts total)
                if (parts.length < 5) {
                    sendMessage("GG|ERROR|Format: GG|CREATE_ROOM|Name|MaxPlayers|MaxAttempts");
                    return;
                }
                String roomName = parts[2];
                int maxPlayers = Integer.parseInt(parts[3]);
                int maxAttempts = Integer.parseInt(parts[4]);
                TCPServer.activeRooms.put(roomName, new GameRoom(roomName, this.playerName, maxPlayers, maxAttempts));
                sendMessage("GG|ROOM_CREATED|" + roomName);
            case "LIST_ROOMS":
                String roomList = String.join(",", TCPServer.activeRooms.keySet());
                sendMessage("GG|ROOM_LIST|" + roomList);
                break;
            case "JOIN_ROOM":
                GameRoom roomToJoin = TCPServer.activeRooms.get(parts[2]); 
                if (roomToJoin != null && roomToJoin.addPlayer(this.playerName)) {
                    sendMessage("GG|JOINED_ROOM|" + roomToJoin.roomName + "|" + roomToJoin.getPlayersListStr());

                    // --- NEW P2P MATCHMAKING LOGIC ---
                    // Tell the new player to connect to all existing players
                    for (String existingPlayerName : roomToJoin.players) {
                        if (!existingPlayerName.equals(this.playerName)) {
                            ClientManager existingClient = TCPServer.activeClients.get(existingPlayerName);
                            if (existingClient != null) {
                                // Tell this new client to connect to the existing client
                                this.sendMessage("GG|CONNECT_PEER|" + existingClient.playerName + "|" + existingClient.ipAddress + "|" + existingClient.p2pPort);
                                // Tell the existing client to prepare for/connect to the new client
                                existingClient.sendMessage("GG|CONNECT_PEER|" + this.playerName + "|" + this.ipAddress + "|" + this.p2pPort);
                            }
                        }
                    }
                }
                break;
            case "LEAVE_ROOM":
                GameRoom roomToLeave = TCPServer.activeRooms.get(parts[2]);
                if (roomToLeave != null )
                {
                    roomToLeave.removePlayer(this.playerName);
                    sendMessage("GG|LEAVE_ROOM|"+roomToLeave.roomName +" :A BIEN ÉTÉ QUITTÉ");
                }
                break;
            case "PLAY_SERVER":
                try {
                    // Commande attendue : GG|PLAY_SERVER|10
                    int nbTentatives = Integer.parseInt(parts[2]);

                    // On initialise l'objet de jeu
                    this.currentGame = new PlayerVSComputer(nbTentatives);
                    this.isGaming = true;

                    sendMessage("GG|GAME_STARTED|Bonne chance " + this.playerName);
                } catch (Exception e) {
                    sendMessage("GG|ERROR|Usage: GG|PLAY_SERVER|nbTentatives");
                }
                break;

            case "GUESS":
                // Commande attendue : GG|GUESS|RED|BLUE|GREEN|YELLOW
                if (this.currentGame != null) {
                    // Extraction des 4 couleurs (indices 2, 3, 4, 5)
                    if (parts.length >= 6) {
                        String[] playerGuess = {parts[2], parts[3], parts[4], parts[5]};

                        // On demande au moteur de jeu d'analyser le coup
                        String response = this.currentGame.handleGuess(playerGuess);
                        sendMessage(response);

                        // Si c'est fini, on nettoie
                        if (this.currentGame.isGameOver()) {
                            this.currentGame = null;
                            this.isGaming = false;
                        }
                    } else {
                        sendMessage("GG|ERROR|Format incorrect : GG|GUESS|C1|C2|C3|C4");
                    }
                } else {
                    sendMessage("GG|ERROR|Aucune partie en cours contre le serveur.");
                }
                break;

            case "KICK_PLAYER":
                if (parts.length < 4) return;
                GameRoom roomToKickFrom = TCPServer.activeRooms.get(parts[2]);
                String playerToKick = parts[3];

                //on vérifie que la salle existe et que le joueur demandant le kick en est l'admin
                if (roomToKickFrom != null && roomToKickFrom.adminName.equals(this.playerName)) {
                    roomToKickFrom.removePlayer(playerToKick);
                    ClientManager kickedClient = TCPServer.activeClients.get(playerToKick);
                    if (kickedClient != null) {
                        kickedClient.sendMessage("GG|PLAYER_KICKED|" + playerToKick);
                    }
                }
                break;

            case "START_GAME":
                if (parts.length < 3) return;
                GameRoom roomToStart = TCPServer.activeRooms.get(parts[2]);

                if (roomToStart != null) {
                    String playerList = roomToStart.getPlayersListStr();
                    // Indique à ts les joueurs que la partie commence
                    for (String pName : roomToStart.players) {
                        ClientManager pClient = TCPServer.activeClients.get(pName);
                        if (pClient != null) {
                            pClient.sendMessage("GG|GAME_STARTED|" + roomToStart.roomName + "|" + playerList + "|" +roomToStart.maxAttempts);
                        }
                    }
                }
                break;

            default:
                System.out.println("Unknown command: " + command);
        }
    }

    private void sendMessage(String msg) {
        out.println(msg);
    }
}
